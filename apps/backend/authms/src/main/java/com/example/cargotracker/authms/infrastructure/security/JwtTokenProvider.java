package com.example.cargotracker.authms.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public final class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        return generateToken(username, List.of());
    }

    public String generateToken(String username, List<String> roles) {
        var now = new Date();
        var expiry = new Date(now.getTime() + expirationMs);
        // US00-r2: トークンごとに一意の jti を発行し、user_sessions で revoked 管理する
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUsernameFromToken(String token) {
        return extractUsername(token);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object roles = parseClaims(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * トークンの jti（JWT ID）を取得する。US00-r2 でセッション管理に使用。
     */
    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    /**
     * トークンの期限を {@link LocalDateTime} で取得する。
     */
    public LocalDateTime getExpirationFromToken(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(expiration.getTime()), ZoneId.systemDefault());
    }

    /**
     * トークンの発行時刻を {@link LocalDateTime} で取得する。
     */
    public LocalDateTime getIssuedAtFromToken(String token) {
        Date issuedAt = parseClaims(token).getIssuedAt();
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(issuedAt.getTime()), ZoneId.systemDefault());
    }

    public boolean isTokenValid(String token) {
        return validateToken(token);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException _) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
