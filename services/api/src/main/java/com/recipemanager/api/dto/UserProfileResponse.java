package com.recipemanager.api.dto;

import java.util.UUID;

// Returned by GET /auth/me.
// householdId and role come from the JWT claims, not a DB query — so they reflect the
// token's snapshot. EP1-06 users with no household will see null for both fields.
public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        UUID householdId,
        String role
) {}