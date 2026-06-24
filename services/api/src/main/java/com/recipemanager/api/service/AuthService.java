package com.recipemanager.api.service;

import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.dto.AuthResponse;
import com.recipemanager.api.dto.UserProfileResponse;

public interface AuthService {

    // Returns the Google OAuth2 authorization URL the browser should be redirected to.
    String buildGoogleAuthUrl();

    // Exchanges a Google authorization code for a user profile, upserts the user,
    // and returns a JWT access token + refresh token.
    AuthResponse handleGoogleCallback(String code);

    // Validates a refresh token, rotates it, and returns a new JWT pair.
    AuthResponse refreshToken(String refreshToken);

    // Deletes the refresh token from Redis. Idempotent.
    void logout(String refreshToken);

    // Returns the authenticated user's profile from the DB.
    UserProfileResponse getProfile(UserPrincipal principal);
}