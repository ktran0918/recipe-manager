package com.recipemanager.api.dto;

// Request body for POST /auth/refresh and POST /auth/logout.
// Sending the refresh token in the body (not as a query param) keeps it out of server logs.
public record RefreshRequest(String refreshToken) {}
