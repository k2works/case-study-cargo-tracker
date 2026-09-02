package com.example.cargotracker.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.auth.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtIssuerTest {

    private static final String SECRET = "cargo-tracker-development-secret-key-32bytes!";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-03T09:00:00Z"), ZoneOffset.UTC);

    private static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }

    @Test
    @DisplayName("利用者名とロールを載せる")
    void issuesTokenWithRoles() {
        String token = new JwtIssuer(SECRET, Duration.ofMinutes(60), CLOCK)
                .issue("sales01", Set.of(Role.ROLE_SALES), null);

        assertThat(payloadOf(token)).contains("sales01").contains("ROLE_SALES");
    }

    @Test
    @DisplayName("荷主なら荷主 ID を載せる（自社分だけを読む絞り込みに使う）")
    void includesShipperId() {
        String token = new JwtIssuer(SECRET, Duration.ofMinutes(60), CLOCK)
                .issue("shipper01", Set.of(Role.ROLE_SHIPPER), "SHP-000001");

        assertThat(payloadOf(token)).contains("SHP-000001");
    }

    @Test
    @DisplayName("荷主でなければ荷主 ID を載せない")
    void omitsShipperIdWhenAbsent() {
        String token = new JwtIssuer(SECRET, Duration.ofMinutes(60), CLOCK)
                .issue("sales01", Set.of(Role.ROLE_SALES), null);

        assertThat(payloadOf(token)).doesNotContain("shipperId");
    }

    @Test
    @DisplayName("短い署名鍵は起動時に断る")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtIssuer("short", Duration.ofMinutes(60), CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 バイト以上");
    }
}
