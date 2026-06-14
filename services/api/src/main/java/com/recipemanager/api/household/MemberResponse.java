package com.recipemanager.api.household;

import com.recipemanager.api.domain.HouseholdMember;

import java.time.OffsetDateTime;
import java.util.UUID;

// Response DTO for a single household member.
// A static factory (from) keeps mapping logic here rather than in the service.
// C# equivalent: a record with an implicit operator or a static factory method.
//
// NOTE: from() must be called within an active @Transactional boundary — getUser()
// triggers a lazy-load on the User association.
public record MemberResponse(UUID userId, String displayName, String role, OffsetDateTime joinedAt) {

    public static MemberResponse from(HouseholdMember member) {
        return new MemberResponse(
                member.getUser().getId(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
