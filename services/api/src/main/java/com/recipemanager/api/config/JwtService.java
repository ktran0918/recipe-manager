package com.recipemanager.api.config;

import com.recipemanager.api.domain.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

// Handles JWT generation and validation using jjwt (0.12.x API).
// C# equivalent: a service wrapping Microsoft.IdentityModel.Tokens — JwtSecurityTokenHandler,
// TokenValidationParameters, SecurityTokenDescriptor — but with a fluent builder API.
@Service
public class JwtService {

    // @Value("${property.path}") injects from application.yml.
    // C# equivalent: IOptions<JwtSettings> injected via the options pattern.
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiry-minutes}")
    private long accessExpiryMinutes;

    // Derives a SecretKey from the Base64-encoded secret string.
    // jjwt requires a key of at least 256 bits for HS256 — the default in application.yml
    // is long enough; warn if a custom JWT_SECRET is too short.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    // Build a signed JWT with the following claims:
    //   - subject  → userId.toString()
    //   - "household_id" → householdId != null ? householdId.toString() : null
    //   - "role"   → role
    //   - issuedAt → new Date()
    //   - expiration → new Date(System.currentTimeMillis() + accessExpiryMinutes * 60 * 1000L)
    // Sign with getSigningKey() and return the compact string.
    //
    // C# equivalent: new JwtSecurityToken(claims: [...], expires: ..., signingCredentials: ...)
    //   wrapped in new JwtSecurityTokenHandler().WriteToken(token)
    public String generateToken(UUID userId, UUID householdId, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("household_id", householdId != null ? householdId.toString() : null)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiryMinutes * 60 * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    // Throws JwtException (and subclasses) on invalid signature, expired token, or malformed input —
    // the caller (JwtAuthFilter) catches these and returns 401.
    //
    // C# equivalent: tokenHandler.ValidateToken(token, validationParameters, out var validatedToken)
    //   — jjwt throws on failure rather than returning a boolean, so no out-param needed.
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Convenience method used by EP1-06 to reconstruct a UserPrincipal from a token
    // without re-querying the database on every request.
    public UserPrincipal extractPrincipal(String token) {
        Claims claims = validateAndExtract(token);
        UUID userId = UUID.fromString(claims.getSubject());
        String householdIdStr = claims.get("household_id", String.class);
        UUID householdId = householdIdStr != null ? UUID.fromString(householdIdStr) : null;
        String role = claims.get("role", String.class);
        return new UserPrincipal(userId, householdId, role);
    }
}
