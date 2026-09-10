package com.agenticai.chatbot.security.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * JWT utility — token generation, parsing, and validation using jjwt 0.12.x.
 *
 * <h3>jjwt 0.12 API changes (vs 0.11)</h3>
 * <ul>
 *   <li>{@code Jwts.parser().verifyWith(key)} replaces {@code .setSigningKey(key)}</li>
 *   <li>{@code .parseSignedClaims()} replaces {@code .parseClaimsJws()}</li>
 *   <li>{@code Keys.hmacShaKeyFor()} accepts raw bytes from a Base64-decoded secret</li>
 *   <li>{@code Jwts.builder().signWith(key)} (no algorithm arg needed for HMAC)</li>
 * </ul>
 *
 * <p>Configure in {@code application.yml}:
 * <pre>
 * app:
 *   jwt:
 *     secret: "your-256-bit-base64-encoded-secret-key-here"
 *     access-token-expiration-ms: 900000      # 15 minutes
 *     refresh-token-expiration-ms: 604800000  # 7 days
 * </pre>
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    // ── Key ───────────────────────────────────────────────────────────────

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Generation ────────────────────────────────────────────────────────

    /**
     * Generates a short-lived access token (default 15 min).
     *
     * @param userDetails The authenticated user — username and roles are embedded.
     */
    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Generates a long-lived refresh token (default 7 days).
     * Refresh tokens carry only {@code sub} and {@code type=refresh} —
     * no role claims, so they cannot be used to call protected endpoints directly.
     *
     * @param userDetails The authenticated user.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(signingKey())
                .compact();
    }

    // ── Extraction ────────────────────────────────────────────────────────

    /** Extracts the username (subject) from a token. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts the expiration date from a token. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /** Extracts the token type ("access" or "refresh"). */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    /** Generic claim extractor. */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        // jjwt 0.12.x: use parseSignedClaims() + verifyWith()
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ── Validation ────────────────────────────────────────────────────────

    /**
     * Validates a token against the supplied {@link UserDetails}.
     *
     * @return {@code true} if the token is signed correctly, not expired,
     *         and the subject matches the user's username.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates the token signature and structure without user context.
     * Used in the filter to do a fast structural check before loading the user.
     *
     * @return {@code true} if the token parses successfully (not expired, valid sig).
     */
    public boolean isTokenStructureValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT empty/null: {}", e.getMessage());
        }
        return false;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** Returns access-token lifetime in seconds (for the AuthResponse). */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }
}