package com.recipemanager.api.household;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipemanager.api.config.JwtService;
import com.recipemanager.api.config.SecurityConfig;
import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest loads only the web layer — controllers, filters, security config — not services or repos.
// Much faster than @SpringBootTest; right for testing HTTP routing, request mapping, and response shapes.
// C# equivalent: a controller unit test with a trimmed-down WebApplicationFactory.
//
// @MockBean wires a Mockito mock into the Spring context in place of the real bean.
// HouseholdService is the controller's dependency.
// JwtService is required because SecurityConfig loads JwtAuthFilter, which depends on it.
//
// @Import(SecurityConfig.class) — @WebMvcTest only auto-includes beans of specific types
// (controllers, filters, converters, etc.). A plain @Configuration class like SecurityConfig
// is NOT auto-included, so without this the slice falls back to Spring Boot's default
// security chain (CSRF enabled, form login) instead of our stateless JWT chain — every
// POST/PATCH/DELETE then gets rejected by the CSRF filter with an empty 403, even though
// the request is authenticated.
// C# equivalent: explicitly registering your Startup's auth middleware in a
// WebApplicationFactory-based test instead of relying on the default pipeline.
@WebMvcTest(HouseholdController.class)
@Import(SecurityConfig.class)
class HouseholdControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private HouseholdService householdService;
    // Required by JwtAuthFilter — not used in tests, but Spring needs it to build the filter.
    @MockBean private JwtService jwtService;

    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID HOUSEHOLD_ID = UUID.randomUUID();

    // Injects a UserPrincipal into the SecurityContext before MockMvc dispatches the request.
    // JwtAuthFilter sees no Authorization header and passes through without touching the context;
    // authorizeHttpRequests then finds the pre-set auth and allows the request.
    // C# equivalent: setting HttpContext.User = new ClaimsPrincipal(...) in test middleware.
    private static RequestPostProcessor asMember() {
        return withPrincipal(USER_ID, HOUSEHOLD_ID, "member");
    }

    private static RequestPostProcessor asOwner() {
        return withPrincipal(USER_ID, HOUSEHOLD_ID, "owner");
    }

    private static RequestPostProcessor withPrincipal(UUID userId, UUID householdId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, householdId, role);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return authentication(auth);
    }

    // --- POST /households ---

    @Test
    void createHousehold_returnsCreated() throws Exception {
        when(householdService.createHousehold(eq(USER_ID), eq("My Home")))
                 .thenReturn(householdResponse("My Home"));

        mockMvc.perform(post("/households").with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHouseholdRequest("My Home"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Home"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createHousehold_alreadyMember_returns409() throws Exception {
        when(householdService.createHousehold(eq(USER_ID), eq("My Home")))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "User already belongs to a household"));

        mockMvc.perform(post("/households").with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHouseholdRequest("My Home"))))
                .andExpect(status().isConflict());
    }

    // --- GET /households/me ---

    @Test
    void getMyHousehold_returnsHousehold() throws Exception {
        when(householdService.getMyHousehold(HOUSEHOLD_ID))
                .thenReturn(householdResponse("My Home"));

        mockMvc.perform(get("/households/me").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Home"));
    }

    @Test
    void getMyHousehold_noHousehold_returns403() throws Exception {
        mockMvc.perform(get("/households/me")
                        .with(withPrincipal(USER_ID, null, "member")))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /households/me ---

    @Test
    void updateHousehold_ownerSucceeds() throws Exception {
        when(householdService.updateHousehold(HOUSEHOLD_ID, USER_ID, "Renamed"))
                .thenReturn(householdResponse("Renamed"));

        mockMvc.perform(patch("/households/me").with(asOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateHouseholdRequest("Renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void updateHousehold_nonOwner_returns403() throws Exception {
        when(householdService.updateHousehold(HOUSEHOLD_ID, USER_ID, "Renamed"))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Owner only"));

        mockMvc.perform(patch("/households/me").with(asMember())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateHouseholdRequest("Renamed"))))
                .andExpect(status().isForbidden());
    }

    // --- POST /households/me/invite ---

    @Test
    void regenerateInviteCode_returnsNewCode() throws Exception {
        when(householdService.regenerateInviteCode(eq(HOUSEHOLD_ID), eq(USER_ID)))
                .thenReturn("NEWCODE1");

        mockMvc.perform(post("/households/me/invite").with(asOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invite_code").value("NEWCODE1"));
    }

    // --- POST /households/join ---

    @Test
    void joinHousehold_validCode_returnsHousehold() throws Exception {
        when(householdService.joinHousehold(USER_ID, "INVITE01"))
                .thenReturn(householdResponse("Their Home"));

        mockMvc.perform(post("/households/join")
                        .with(withPrincipal(USER_ID, null, "member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinHouseholdRequest("INVITE01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Their Home"));
    }

    @Test
    void joinHousehold_invalidCode_returns404() throws Exception {
        when(householdService.joinHousehold(USER_ID, "BAD_INVITE"))
                .thenThrow(new EntityNotFoundException("Invalid invite code"));

        mockMvc.perform(post("/households/join")
                        .with(withPrincipal(USER_ID, null, "member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinHouseholdRequest("BAD_INVITE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void joinHousehold_alreadyMember_returns409() throws Exception {
        when(householdService.joinHousehold(USER_ID, "INVITE01"))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "Already a member of this household"));

        mockMvc.perform(post("/households/join")
                        .with(withPrincipal(USER_ID, null, "member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinHouseholdRequest("INVITE01"))))
                .andExpect(status().isConflict());
    }

    // --- GET /households/me/members ---

    @Test
    void listMembers_returnsMembers() throws Exception {
        UUID memberId = UUID.randomUUID();
        List<MemberResponse> members = List.of(
            new MemberResponse(memberId, "Alice", "member", OffsetDateTime.now()));

        when(householdService.listMembers(HOUSEHOLD_ID)).thenReturn(members);

        mockMvc.perform(get("/households/me/members").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].display_name").value("Alice"));
    }

    // --- DELETE /households/me/members/{userId} ---

    @Test
    void removeMember_ownerRemovesOther_returns204() throws Exception {
        UUID targetId = UUID.randomUUID();
        doNothing().when(householdService).removeMember(HOUSEHOLD_ID, USER_ID, targetId);

        mockMvc.perform(delete("/households/me/members/" + targetId).with(asOwner()))
                   .andExpect(status().isNoContent());
    }

    @Test
    void removeMember_self_returns400() throws Exception {
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "Cannot remove yourself from the household"))
                .when(householdService).removeMember(HOUSEHOLD_ID, USER_ID, USER_ID);

        mockMvc.perform(delete("/households/me/members/" + USER_ID).with(asOwner()))
                .andExpect(status().isBadRequest());
    }

    // Builds a stubbed HouseholdResponse for use in when(...).thenReturn(...) calls.
    private HouseholdResponse householdResponse(String name) {
        return new HouseholdResponse(HOUSEHOLD_ID, name, "INVITE01", OffsetDateTime.now(), List.of());
    }
}
