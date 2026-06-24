package com.recipemanager.api.controller;

import com.recipemanager.api.domain.UserPrincipal;
import com.recipemanager.api.dto.AuthResponse;
import com.recipemanager.api.dto.RefreshRequest;
import com.recipemanager.api.dto.UserProfileResponse;
import com.recipemanager.api.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// Thin controller — all business logic lives in AuthService.
// C# equivalent: [ApiController] [Route("auth")] public class AuthController : ControllerBase
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Redirect the browser to Google's consent screen.
    // HttpServletResponse.sendRedirect() writes a 302 response directly — no return value needed.
    // C# equivalent: return Redirect(authService.BuildGoogleAuthUrl());
    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect(authService.buildGoogleAuthUrl());
    }

    // Google posts back here with ?code=... after the user consents.
    // Exchanges the code for a user profile and issues a JWT pair.
    @GetMapping("/google/callback")
    public ResponseEntity<AuthResponse> googleCallback(@RequestParam String code) {
        return ResponseEntity.ok(authService.handleGoogleCallback(code));
    }

    // Issues a new access JWT and rotates the refresh token.
    // Refresh token is in the request body — never in a query param (would appear in logs).
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest body) {
        return ResponseEntity.ok(authService.refreshToken(body.refreshToken()));
    }

    // Deletes the refresh token from Redis. Returns 204 No Content.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest body) {
        authService.logout(body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // Returns the current user's profile.
    // @AuthenticationPrincipal resolves the UserPrincipal that JwtAuthFilter placed in the
    // SecurityContext. No manual token parsing needed here.
    // C# equivalent: User.FindFirstValue(ClaimTypes.NameIdentifier) — but typed and compile-safe.
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getProfile(principal));
    }
}