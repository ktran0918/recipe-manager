package com.recipemanager.api.household;

import com.recipemanager.api.domain.Household;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Top-level response DTO — wraps the household plus its full member list.
// from() must be called within an active @Transactional boundary because
// Household.getMembers() is LAZY and MemberResponse.from() accesses member.getUser().
public record HouseholdResponse(UUID id, String name, String inviteCode, OffsetDateTime createdAt,
                                 List<MemberResponse> members) {

    public static HouseholdResponse from(Household household) {
        List<MemberResponse> members = household.getMembers().stream()
                .map(MemberResponse::from)
                .toList();
        return new HouseholdResponse(
                household.getId(),
                household.getName(),
                household.getInviteCode(),
                household.getCreatedAt(),
                members
        );
    }
}
