package com.recipemanager.api.dto;

// Returned by POST /auth/google/callback and POST /auth/refresh.
// C# equivalent: a record returned from an IActionResult in an API controller.
public record AuthResponse(String accessToken, String refreshToken, String tokenType) {

    // Convenience constructor — callers don't need to spell out "Bearer" every time.
    public AuthResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer");
    }
}