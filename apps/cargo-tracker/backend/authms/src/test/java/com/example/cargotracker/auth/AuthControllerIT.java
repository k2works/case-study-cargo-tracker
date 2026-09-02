package com.example.cargotracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import javax.sql.DataSource;

/** ログイン（US26）。失敗理由を出し分けないことと、ロックが効くことを固定する。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// クラスが終わったらコンテキストを閉じる。閉じないと複数のコンテキストが同時に
// 生きたまま同じ Axon Server にハンドラを登録し、二重登録で起動に失敗する
// （DuplicateQueryHandlerSubscriptionException）。落ちるテストが実行順で変わる。
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        // 監査ログはテストをまたいで残るので毎回消す。残すと件数の検査が
        // 前のテストの分を数え、原因でないテストが落ちる。
        jdbc.update("DELETE FROM auth_audit_log");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("""
                INSERT INTO users (username, password_hash, display_name, shipper_id, enabled,
                                   failed_attempts, locked_until, created_at, updated_at)
                VALUES (?, ?, ?, NULL, TRUE, 0, NULL, ?, ?)
                """, "sales01", passwordEncoder.encode("secret1234"), "営業 太郎",
                OffsetDateTime.now(), OffsetDateTime.now());
        jdbc.update("INSERT INTO user_roles (username, role) VALUES (?, ?)", "sales01", "ROLE_SALES");
    }

    private ResponseEntity<JsonMap> login(String username, String password) {
        return rest.post()
                .uri("http://localhost:" + port + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .retrieve()
                .toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("正しい資格情報なら JWT とロールを返す")
    void signsIn() {
        ResponseEntity<JsonMap> response = login("sales01", "secret1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("token").toString()).contains(".");
        assertThat(response.getBody().get("roles").toString()).contains("ROLE_SALES");
        assertThat(response.getBody().get("displayName")).isEqualTo("営業 太郎");
    }

    @Test
    @DisplayName("利用者が居ない場合とパスワードが違う場合で応答が変わらない")
    void doesNotRevealWhetherUserExists() {
        ResponseEntity<JsonMap> unknownUser = login("nosuchuser", "secret1234");
        ResponseEntity<JsonMap> wrongPassword = login("sales01", "wrong-password");

        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownUser.getBody())
                .as("出し分けると利用者名の存在を教えてしまう")
                .isEqualTo(wrongPassword.getBody());
    }

    @Test
    @DisplayName("失敗が続くとロックされ、正しいパスワードでも入れない")
    void locksAfterRepeatedFailures() {
        for (int i = 0; i < 5; i++) {
            login("sales01", "wrong-password");
        }

        ResponseEntity<JsonMap> afterLock = login("sales01", "secret1234");

        assertThat(afterLock.getStatusCode())
                .as("ロックが効かないと総当たりを止められない")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject(
                "SELECT locked_until IS NOT NULL FROM users WHERE username = 'sales01'",
                Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("成功で失敗回数が戻る")
    void resetsFailedAttemptsOnSuccess() {
        login("sales01", "wrong-password");
        login("sales01", "secret1234");

        assertThat(jdbc.queryForObject(
                "SELECT failed_attempts FROM users WHERE username = 'sales01'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("成功も失敗も記録に残る")
    void writesAuditLog() {
        login("sales01", "wrong-password");
        login("sales01", "secret1234");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auth_audit_log WHERE username = 'sales01' AND succeeded = FALSE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auth_audit_log WHERE username = 'sales01' AND succeeded = TRUE",
                Integer.class)).isEqualTo(1);
    }
}
