package com.recipemanager.api.scrape;

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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScrapeJobController.class)
@Import(SecurityConfig.class)
class ScrapeJobControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ScrapeJobService scrapeJobService;
    @MockBean private JwtService jwtService;

    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID HOUSEHOLD_ID = UUID.randomUUID();
    private static final UUID JOB_ID       = UUID.randomUUID();

    private static RequestPostProcessor asMember() {
        UserPrincipal principal = new UserPrincipal(USER_ID, HOUSEHOLD_ID, "member");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("member")));
        return authentication(auth);
    }

    @Test
    void submitParse_validUrl_returnsAccepted() throws Exception {
        when(scrapeJobService.submitParse(HOUSEHOLD_ID, USER_ID, "https://example.com/recipe"))
                .thenReturn(new ParseResponse(JOB_ID, "pending", null));

        mockMvc.perform(post("/recipes/parse")
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/recipe"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void submitParse_invalidUrl_returns400() throws Exception {
        when(scrapeJobService.submitParse(any(), any(), eq("not-a-url")))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Invalid URL: not-a-url"));

        mockMvc.perform(post("/recipes/parse")
                        .with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "not-a-url"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJobStatus_returnsStatus() throws Exception {
        when(scrapeJobService.getJobStatus(JOB_ID, HOUSEHOLD_ID))
                .thenReturn(new JobStatusResponse(JOB_ID, "parsing", null, null));

        mockMvc.perform(get("/recipes/jobs/" + JOB_ID).with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("parsing"));
    }

    @Test
    void getJobStatus_wrongHousehold_returns404() throws Exception {
        when(scrapeJobService.getJobStatus(JOB_ID, HOUSEHOLD_ID))
                .thenThrow(new EntityNotFoundException("Job not found"));

        mockMvc.perform(get("/recipes/jobs/" + JOB_ID).with(asMember()))
                .andExpect(status().isNotFound());
    }
}