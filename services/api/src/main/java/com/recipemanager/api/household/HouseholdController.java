package com.recipemanager.api.household;

import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// @RestController = @Controller + @ResponseBody — every return value is serialized to JSON.
// @RequestMapping sets the base path shared by all methods in this class.
// C# equivalent: [ApiController] [Route("households")]
@RestController
@RequestMapping("/households")
public class HouseholdController {

    private final HouseholdService householdService;

    public HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    // @PostMapping maps HTTP POST to this method.
    // @RequestBody deserializes the JSON body into CreateHouseholdRequest.
    //   C# equivalent: [FromBody] CreateHouseholdRequest request
    // @AuthenticationPrincipal injects the principal stored in SecurityContextHolder by JwtAuthFilter.
    //   C# equivalent: HttpContext.User, but typed — no string key lookups needed.
    // ResponseEntity.status(201).body(...) sets status + body explicitly.
    //   C# equivalent: return StatusCode(201, response) or return Created(uri, response).
    @PostMapping
    public ResponseEntity<HouseholdResponse> createHousehold(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateHouseholdRequest request) {
        HouseholdResponse response = householdService.createHousehold(principal.userId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<HouseholdResponse> getMyHousehold(@AuthenticationPrincipal UserPrincipal principal) {
        requireHousehold(principal);

        HouseholdResponse response = householdService.getMyHousehold(principal.householdId());
        return ResponseEntity.ok(response);
    }

    // @PatchMapping maps HTTP PATCH — partial update (name only).
    // C# equivalent: [HttpPatch("me")]
    @PatchMapping("/me")
    public ResponseEntity<HouseholdResponse> updateHousehold(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateHouseholdRequest request) {
        requireHousehold(principal);

        HouseholdResponse response = householdService.updateHousehold(principal.householdId(), principal.userId(),
                request.name());
       return ResponseEntity.ok(response);
    }

    @PostMapping("/me/invite")
    public ResponseEntity<InviteCodeResponse> regenerateInviteCode(
            @AuthenticationPrincipal UserPrincipal principal) {
        requireHousehold(principal);

        String newCode = householdService.regenerateInviteCode(principal.householdId(), principal.userId());
        return ResponseEntity.ok(new InviteCodeResponse(newCode));
    }

    @PostMapping("/join")
    public ResponseEntity<HouseholdResponse> joinHousehold(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody JoinHouseholdRequest request) {
        HouseholdResponse response = householdService.joinHousehold(principal.userId(), request.inviteCode());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/members")
    public ResponseEntity<List<MemberResponse>> listMembers(@AuthenticationPrincipal UserPrincipal principal) {
        requireHousehold(principal);
        List<MemberResponse> members = householdService.listMembers(principal.householdId());
        return ResponseEntity.ok(members);
    }

    // @PathVariable binds the {userId} URL segment to the method parameter.
    // C# equivalent: [FromRoute] Guid userId
    // ResponseEntity.noContent().build() produces HTTP 204 with no body.
    // C# equivalent: return NoContent()
    @DeleteMapping("/me/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId) {
        requireHousehold(principal);
        householdService.removeMember(principal.householdId(), principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    private static void requireHousehold(UserPrincipal principal) {
        if (principal.householdId() == null)
            throw new ApiException(HttpStatus.FORBIDDEN, "Not a member of any household");
    }
}
