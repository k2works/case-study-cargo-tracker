package com.example.authms.infrastructure.security;

import com.example.authms.application.port.TokenIssuer;
import com.example.authms.domain.model.Role;
import com.example.authms.domain.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT を発行する。鍵を持つのは authms（発行）と gatewayms（検証）だけである（ADR-004）。
 * 署名の検証は Gateway が行うため、authms は検証側の実装を持たない。
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final SecretKey key;
    private final Duration validity;
    private final Clock clock;

    public JwtTokenIssuer(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.validity-minutes:480}") long validityMinutes,
            Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = Duration.ofMinutes(validityMinutes);
        this.clock = clock;
    }

    @Override
    public String issue(User user) {
        var now = clock.instant();
        List<String> roles = user.roles().stream().map(Role::name).sorted().toList();
        return Jwts.builder()
                .subject(user.username())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }
}
