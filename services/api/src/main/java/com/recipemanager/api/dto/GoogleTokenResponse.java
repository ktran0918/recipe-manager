package com.recipemanager.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Maps the JSON body returned by https://oauth2.googleapis.com/token.
// Jackson's @JsonProperty bridges Google's snake_case keys to camelCase fields.
// C# equivalent: a class with [JsonPropertyName("access_token")] attributes.
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type")   String tokenType,
        @JsonProperty("expires_in")   int    expiresIn,
        @JsonProperty("id_token")     String idToken
) {}