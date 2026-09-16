package com.example.cargotracker.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtSecretTest {

    private static final String REAL = "a-real-secret-that-is-long-enough-32!";

    @Test
    @DisplayName("本番相当では開発用の既定値を拒む")
    void rejectsDevelopmentSecretInProduction() {
        assertThatThrownBy(() -> JwtSecret.of(null, true))
                .as("既定値を許すと、環境変数を渡し忘れても既知の鍵で起動が成功する")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARGOTRACKER_JWT_SECRET");

        assertThatThrownBy(() -> JwtSecret.of(JwtSecret.DEVELOPMENT_SECRET, true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtSecret.of("   ", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("本番相当でも設定されていれば通す")
    void acceptsConfiguredSecretInProduction() {
        assertThat(JwtSecret.of(REAL, true).value()).isEqualTo(REAL);
    }

    @Test
    @DisplayName("開発では既定値を使ってよい")
    void allowsDevelopmentSecretOutsideProduction() {
        assertThat(JwtSecret.of(null, false).value()).isEqualTo(JwtSecret.DEVELOPMENT_SECRET);
        assertThatCode(() -> JwtSecret.of("", false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("短い鍵はどの環境でも拒む")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> JwtSecret.of("short", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 バイト以上");
    }
}
