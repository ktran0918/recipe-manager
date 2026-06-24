package com.recipemanager.api.dto;

// Maps the JSON body returned by https://www.googleapis.com/oauth2/v3/userinfo.
// "sub" is the stable, immutable Google user ID — use this as the oauth_id in the users table,
// not the email, which can change.
public record GoogleUserInfo(String sub, String email, String name, String picture) {}