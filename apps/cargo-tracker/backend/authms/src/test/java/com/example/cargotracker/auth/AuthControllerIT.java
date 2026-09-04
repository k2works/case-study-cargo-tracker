package com.example.cargotracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.auth.infrastructure.config.DemoUserSeedConfiguration;
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
    private org.springframework.context.ApplicationContext context;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private JdbcTemplate jdbc;

    @Test
    @DisplayName("動作確認用の利用者は既定では読み込まない")
    void doesNotSeedDemoUsersByDefault() {
        // 既定を安全側に倒していることを、設定ファイルの字面ではなく実際の
        // コンテキストで確かめる。パスワードが分かっている利用者が業務環境に
        // 入る経路は、書き忘れでは開かない（ADR-0004 決定 1）。
        assertThat(context.getBeanNamesForType(DemoUserSeedConfiguration.class)).isEmpty();
    }

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

        assertThat(events("sales01")).containsExactly("LOGIN_FAILURE", "LOGIN_SUCCESS");
    }

    // --- US31 ここから -------------------------------------------------------

    /** 監査ログの種別を発生順に返す。 */
    private java.util.List<String> events(String username) {
        return jdbc.queryForList(
                "SELECT event_type FROM auth_audit_log WHERE username = ? ORDER BY audit_id",
                String.class, username);
    }

    /** 監査ログの理由を発生順に返す。{@code null} も含める。 */
    private java.util.List<String> reasons(String username) {
        return jdbc.queryForList(
                "SELECT reason FROM auth_audit_log WHERE username = ? ORDER BY audit_id",
                String.class, username);
    }

    private void insertDisabledUser() {
        jdbc.update("""
                INSERT INTO users (username, password_hash, display_name, shipper_id, enabled,
                                   failed_attempts, locked_until, created_at, updated_at)
                VALUES (?, ?, ?, NULL, FALSE, 0, NULL, ?, ?)
                """, "retired01", passwordEncoder.encode("secret1234"), "退職 済",
                OffsetDateTime.now(), OffsetDateTime.now());
        jdbc.update("INSERT INTO user_roles (username, role) VALUES (?, ?)",
                "retired01", "ROLE_SALES");
    }

    @Test
    @DisplayName("ロックの長さは非機能要件どおり 15 分")
    void locksForFifteenMinutes() {
        // 長さを実装の都合で決めると、設計の数字が守られているか誰も見なくなる。
        // 30 分に戻すとこのテストが赤になる。
        for (int i = 0; i < 5; i++) {
            login("sales01", "wrong-password");
        }

        java.time.OffsetDateTime lockedUntil = jdbc.queryForObject(
                "SELECT locked_until FROM users WHERE username = 'sales01'",
                java.time.OffsetDateTime.class);
        long minutes = java.time.Duration.between(java.time.OffsetDateTime.now(), lockedUntil)
                .toMinutes();

        assertThat(minutes)
                .as("non_functional.md「認証失敗 5 回で 15 分ロック」")
                .isBetween(13L, 15L);
    }

    @Test
    @DisplayName("ロックしたこと自体が記録に残る")
    void writesLockedToAuditLog() {
        // 「失敗が 5 件ある」と「ロックした」は別の事実。前者だけだと、
        // 運用が「この利用者はいつロックされたのか」に答えられない。
        for (int i = 0; i < 5; i++) {
            login("sales01", "wrong-password");
        }

        assertThat(events("sales01")).containsExactly(
                "LOGIN_FAILURE", "LOGIN_FAILURE", "LOGIN_FAILURE", "LOGIN_FAILURE",
                "LOGIN_FAILURE", "LOCKED");
    }

    @Test
    @DisplayName("断った理由は記録にだけ残す")
    void writesReasonToAuditLogOnly() {
        // 利用者に返すメッセージは同一だが、記録では区別できなければならない
        // （US31 §受入基準 7）。区別が付かないと、総当たりと打ち間違いを
        // 見分けられない。
        insertDisabledUser();

        login("sales01", "wrong-password");
        for (int i = 0; i < 5; i++) {
            login("sales01", "wrong-password");
        }
        login("sales01", "secret1234");
        login("retired01", "secret1234");

        assertThat(reasons("sales01"))
                .as("6 回目以降はロック中なので理由が変わる")
                .containsSubsequence("BAD_CREDENTIALS", "LOCKED");
        assertThat(reasons("retired01")).containsExactly("DISABLED");
    }

    private ResponseEntity<JsonMap> unlock(String username, String roles) {
        return rest.post()
                .uri("http://localhost:" + port + "/api/v1/auth/admin/users/" + username + "/unlock")
                .header("X-Auth-Roles", roles)
                .retrieve()
                .toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> adminUsers(String roles) {
        return rest.get()
                .uri("http://localhost:" + port + "/api/v1/auth/admin/users")
                .header("X-Auth-Roles", roles)
                .retrieve()
                .toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("管理者が解除すると入れるようになる")
    void adminUnlocksAccount() {
        // 時間経過だけが解除の手段だと、業務中にロックされた担当者は 15 分待つしかない
        // （US31 §受入基準 4）。
        for (int i = 0; i < 5; i++) {
            login("sales01", "wrong-password");
        }
        assertThat(login("sales01", "secret1234").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(unlock("sales01", "ROLE_ADMIN").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login("sales01", "secret1234").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(events("sales01")).contains("UNLOCKED");
    }

    @Test
    @DisplayName("管理者以外は解除できない")
    void rejectsUnlockByNonAdmin() {
        assertThat(unlock("sales01", "ROLE_SALES").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unlock("sales01", null).getStatusCode())
                .as("ヘッダが無いのは「ロールが無い」であって「全部許す」ではない")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("居ない利用者の解除は 404 ではなく 204 で返す")
    void unlockIsIdempotentForUnknownUser() {
        // 404 と 204 を出し分けると、管理画面を踏み台に利用者名を総当たりできる。
        // 解除は「その利用者がロックされていない状態にする」ことなので、
        // 居なければ既にその状態である。
        assertThat(unlock("nosuchuser", "ROLE_ADMIN").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(events("nosuchuser")).isEmpty();
    }

    @Test
    @DisplayName("利用者管理は管理者だけが読める")
    void listsUsersForAdminOnly() {
        assertThat(adminUsers("ROLE_SALES").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonMap> response = adminUsers("ROLE_ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("users").toString()).contains("sales01");
    }

    @Test
    @DisplayName("複数ロールの管理者も解除できる")
    void allowsUnlockWhenAdminAmongRoles() {
        // ロールは "," 区切りで届く。1 つ目だけを見る実装だと、管理者を兼ねる
        // 利用者が解除できない。
        assertThat(unlock("sales01", "ROLE_SALES,ROLE_ADMIN").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unlock("sales01", " ROLE_ADMIN ").getStatusCode())
                .as("前後の空白で弾かない")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("利用者管理はロールと状態を出す")
    void showsRolesAndLockState() {
        insertDisabledUser();
        for (int i = 0; i < 5; i++) {
            login("sales01", "wrong-password");
        }

        ResponseEntity<JsonMap> response = adminUsers("ROLE_ADMIN");

        String body = response.getBody().toString();
        assertThat(body).contains("ROLE_SALES");
        assertThat(body)
                .as("ロック中であることが画面で判断できないと、解除ボタンを出せない")
                .contains("locked=true");
        assertThat(body).contains("retired01").contains("enabled=false");
    }

    @Test
    @DisplayName("無効化されたアカウントは正しいパスワードでも入れない")
    void rejectsDisabledAccount() {
        insertDisabledUser();

        ResponseEntity<JsonMap> response = login("retired01", "secret1234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .as("無効化を教えると、生きている利用者名を探る手がかりになる")
                .isEqualTo(login("sales01", "wrong-password").getBody());
    }
}
