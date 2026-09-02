package com.example.gatewayms.security;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

/** JWT の署名鍵。鍵を保持するのは authms（発行）と gatewayms（検証）だけである（ADR-004）。 */
public final class JwtKeys {

    private JwtKeys() {
    }

    public static SecretKey hmacKeyOf(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
