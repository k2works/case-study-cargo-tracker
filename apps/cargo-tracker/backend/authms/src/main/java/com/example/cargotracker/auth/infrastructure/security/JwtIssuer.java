package com.example.cargotracker.auth.infrastructure.security;

import com.example.cargotracker.shared.domain.auth.Role;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** JWT の発行。検証は Gateway が行う（ADR-0001 決定 4 の分担）。 */
public class JwtIssuer {

    private final SecretKey key;
    private final Duration validity;
    private final Clock clock;

    public JwtIssuer(String secret, Duration validity, Clock clock) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // 短い鍵は署名の強度を下げる。起動時に落として気づけるようにする。
            throw new IllegalArgumentException("JWT の署名鍵は 32 バイト以上が要ります");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.validity = validity;
        this.clock = clock;
    }

    public String issue(String username, Set<Role> roles, String shipperId) {
        var now = clock.instant();
        var builder = Jwts.builder()
                .subject(username)
                .claim("roles", roles.stream().map(Role::name).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)));
        if (shipperId != null) {
            builder.claim("shipperId", shipperId);
        }
        return builder.signWith(key).compact();
    }
}
