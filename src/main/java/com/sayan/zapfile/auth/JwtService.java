package com.sayan.zapfile.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessExpiry;
    private final Duration refreshExpiry;

    public JwtService(@Value("${zapfile.jwt.secret}") String secret,
                      @Value("${zapfile.jwt.access-expiry-minutes}") long accessExpiryMinutes,
                      @Value("${zapfile.jwt.refresh-expiry-days}") long refreshExpiryDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiry = Duration.ofMinutes(accessExpiryMinutes);
        this.refreshExpiry = Duration.ofDays(refreshExpiryDays);
    }

    public String createAccessToken(String userId) {
        return createToken(userId, TYPE_ACCESS, accessExpiry);
    }

    public String createRefreshToken(String userId) {
        return createToken(userId, TYPE_REFRESH, refreshExpiry);
    }

    private String createToken(String userId, String type, Duration expiry) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the user id if the token is valid, signed, unexpired and of the expected type.
     */
    public Optional<String> extractUserId(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
