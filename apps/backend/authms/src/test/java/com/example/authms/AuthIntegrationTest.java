package com.example.authms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authms.application.internal.LoginResult;
import com.example.authms.application.internal.LoginUseCase;
import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.Role;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway で構築した実スキーマに対してログインが成立することを確認する。
 *
 * <p>ユニットテストが緑でも、マイグレーション・マッピング・シードのいずれかが噛み合わなければ
 * 誰もログインできない。実際の DB（PostgreSQL）で通しておく。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("認証の結合")
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private UserRepository users;

    @Test
    @DisplayName("初期利用者はシードのパスワードでログインできる")
    void seedUserCanLogIn() {
        Optional<LoginResult> result = loginUseCase.login("sales01", "password");

        assertThat(result).as("シードの BCrypt ハッシュが実際のパスワードと一致していない").isPresent();
        assertThat(result.orElseThrow().roles()).containsExactly(Role.ROLE_SALES);
        assertThat(result.orElseThrow().displayName()).isEqualTo("山田太郎");
        assertThat(result.orElseThrow().token()).isNotBlank();
    }

    @Test
    @DisplayName("誤ったパスワードでは失敗し、失敗回数が永続化される")
    void persistsFailedAttempts() {
        loginUseCase.login("tracker01", "wrong");

        assertThat(users.findByUsername("tracker01").orElseThrow().failedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("5 回連続で失敗するとロック期限が永続化される")
    void persistsLock() {
        for (int i = 0; i < 5; i++) {
            loginUseCase.login("handler01", "wrong");
        }

        assertThat(users.findByUsername("handler01").orElseThrow().lockedUntil())
                .as("ロック期限が保存されていない。プロセスをまたぐと保護が消える")
                .isNotNull();
    }
}
