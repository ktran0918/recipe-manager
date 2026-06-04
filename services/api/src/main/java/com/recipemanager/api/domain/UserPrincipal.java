package com.recipemanager.api.domain;

import java.util.UUID;

// The authenticated identity extracted from a validated JWT.
// Injected into controller methods via @AuthenticationPrincipal — Spring reads it from
// the SecurityContext that JwtAuthFilter populates on each request.
//
// C# equivalent: ClaimsPrincipal, but typed. Instead of principal.FindFirst("sub").Value
// you get principal.userId() — compile-time safe, no string keys.
//
// householdId is nullable — a user who just registered via OAuth has no household yet
// (they haven't called POST /households). Controllers that require a household must
// check for null and return 403.
public record UserPrincipal(UUID userId, UUID householdId, String role) {}
