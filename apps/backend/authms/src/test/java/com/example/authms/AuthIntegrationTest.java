package com.example.authms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authms.application.internal.LoginResult;
import com.example.authms.application.internal.LoginUseCase;
import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.AuthEventType;
import com.example.authms.domain.model.Role;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long auditCount(String username, AuthEventType eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_audit_log WHERE username = ? AND event_type = ?",
                Long.class, username, eventType.name());
    }

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
    @DisplayName("無効化された初期利用者はログインできない")
    void disabledSeedUserCannotLogIn() {
        // ログイン画面の一覧に「無効化されたアカウント」として載せている以上、
        // 実際にログインできないことまで確かめる。載せただけで挙動が違えば確認の役に立たない
        assertThat(loginUseCase.login("disabled01", "password")).isEmpty();
        assertThat(auditCount("disabled01", AuthEventType.DISABLED_ATTEMPT)).isEqualTo(1);
    }

    @Test
    @DisplayName("認証事象が監査ログの行として実際に書かれる")
    void writesAuditLogRows() {
        // 記録の失敗を握り潰す実装のため、「例外が出ない」ことは「記録された」ことを意味しない。
        // 画面に理由を出さない以上、ここが唯一の手がかりになるので行の存在で確かめる。
        long before = auditCount("accountant01", AuthEventType.LOGIN_SUCCESS);

        loginUseCase.login("accountant01", "password");

        assertThat(auditCount("accountant01", AuthEventType.LOGIN_SUCCESS)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("未登録の利用者名での試行も監査ログに残る")
    void writesAuditLogForUnknownUser() {
        loginUseCase.login("no-such-user", "password");

        assertThat(auditCount("no-such-user", AuthEventType.LOGIN_FAILURE)).isEqualTo(1);
    }

    @Test
    @DisplayName("ロック中は正しいパスワードでも拒否する")
    void rejectsCorrectPasswordWhileLocked() {
        for (int i = 0; i < 5; i++) {
            loginUseCase.login("shipper01", "wrong");
        }

        assertThat(loginUseCase.login("shipper01", "password"))
                .as("ロック中に正しいパスワードで入れてしまう")
                .isEmpty();
        assertThat(auditCount("shipper01", AuthEventType.LOCKED)).isEqualTo(1);
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
