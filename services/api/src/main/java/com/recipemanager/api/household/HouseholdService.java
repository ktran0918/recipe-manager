package com.recipemanager.api.household;

import java.util.List;
import java.util.UUID;

// Service interface — controller depends on this type, not the implementation.
// C# equivalent: IHouseholdService registered as
//   builder.Services.AddScoped<IHouseholdService, HouseholdServiceImpl>();
// In Spring you only need the @Service annotation on the impl — no registration call.
public interface HouseholdService {

    HouseholdResponse createHousehold(UUID userId, String name);

    HouseholdResponse getMyHousehold(UUID householdId);

    HouseholdResponse updateHousehold(UUID householdId, UUID callerId, String name);

    // Returns the new invite code so the controller can echo it without re-fetching the household.
    String regenerateInviteCode(UUID householdId, UUID callerId);

    HouseholdResponse joinHousehold(UUID userId, String inviteCode);

    List<MemberResponse> listMembers(UUID householdId);

    void removeMember(UUID householdId, UUID callerId, UUID targetUserId);
}
