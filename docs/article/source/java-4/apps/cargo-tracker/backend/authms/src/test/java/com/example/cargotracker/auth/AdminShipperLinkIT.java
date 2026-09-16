package com.example.cargotracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 利用者を荷主に紐付ける（US18・US23・US37 の前提）。
 *
 * <p><b>この列は読まれるだけで、どこからも書かれていなかった。</b> 紐付けが
 * 無いと Gateway は {@code X-Auth-Shipper-Id} を載せられず、<b>荷主向けの画面が
 * すべて 403 になる</b>——自社予約（S45・S46）・自社請求書（S62）・荷主の
 * 追跡一覧・貨物の知らせ。クラスタで荷主としてログインして初めて分かった
 * （<b>定義済み未使用は配線漏れのサイン</b>）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminShipperLinkIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private java.time.Clock clock;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM auth_audit_log");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("""
                INSERT INTO users (username, password_hash, display_name, shipper_id, enabled,
                                   failed_attempts, locked_until, created_at, updated_at)
                VALUES (?, ?, ?, NULL, TRUE, 0, NULL, ?, ?)
                """, "shipper01", passwordEncoder.encode("secret1234"), "荷主 五郎",
                OffsetDateTime.now(clock), OffsetDateTime.now(clock));
        jdbc.update("INSERT INTO user_roles (username, role) VALUES (?, ?)",
                "shipper01", "ROLE_SHIPPER");
    }

    private ResponseEntity<Void> link(String username, Object body, String roles) {
        var request = rest.post()
                .uri("http://localhost:" + port + "/api/v1/auth/admin/users/"
                        + username + "/shipper")
                .contentType(MediaType.APPLICATION_JSON);
        if (roles != null) {
            request = request.header("X-Auth-Roles", roles);
        }
        return request.body(body == null ? Map.of() : body).retrieve().toBodilessEntity();
    }

    private String linkedShipperId() {
        return jdbc.queryForObject(
                "SELECT shipper_id FROM users WHERE username = ?", String.class, "shipper01");
    }

    @Test
    @DisplayName("US18 の前提: 管理者が利用者を荷主に紐付けると、ログインの応答に載る")
    void linksTheShipperAndIssuesItInTheToken() {
        assertThat(link("shipper01", Map.of("shipperId", "SHP-000001"), "ROLE_ADMIN")
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(linkedShipperId()).isEqualTo("SHP-000001");

        // **ログインの応答に載る。** Gateway はここから X-Auth-Shipper-Id を作る
        // ——載らなければ荷主向けの画面は全部 403 のままである。
        ResponseEntity<JsonMap> response = rest.post()
                .uri("http://localhost:" + port + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "shipper01", "password", "secret1234"))
                .retrieve().toEntity(JsonMap.class);
        assertThat(response.getBody()).containsEntry("shipperId", "SHP-000001");
    }

    @Test
    @DisplayName("紐付けは外せる（付けるだけの入口にしない）")
    void unlinksWhenTheShipperIsBlank() {
        link("shipper01", Map.of("shipperId", "SHP-000001"), "ROLE_ADMIN");

        assertThat(link("shipper01", Map.of("shipperId", "  "), "ROLE_ADMIN")
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(linkedShipperId())
                .as("間違えたときに直す手立てが無い入口にしない")
                .isNull();
    }

    @Test
    @DisplayName("管理者以外は紐付けられない（認可は入力検証より先）")
    void refusesEveryoneButTheAdministrator() {
        // **静かに効く操作である。** 紐付けを変えれば、その利用者は別の荷主の
        // 予約と請求書を読めるようになる。
        for (String roles : java.util.List.of("ROLE_SALES", "ROLE_SHIPPER", "ROLE_TRACKER")) {
            assertThat(link("shipper01", Map.of("shipperId", "SHP-000002"), roles)
                    .getStatusCode())
                    .as("%s が紐付けられる", roles)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(link("shipper01", Map.of("shipperId", "SHP-000002"), null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(linkedShipperId()).isNull();
    }

    @Test
    @DisplayName("居ない利用者でも 204（利用者名を総当たりさせない）")
    void staysQuietForUnknownUsers() {
        assertThat(link("nobody", Map.of("shipperId", "SHP-000001"), "ROLE_ADMIN")
                .getStatusCode())
                .as("404 と出し分けると、管理画面を踏み台に利用者名を探せる")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("紐付けは監査ログに残る（どの荷主に付けたかまで）")
    void recordsWhichShipperWasLinked() {
        // **静かに効く操作こそ、あとから誰が何をしたか辿れなければならない。**
        // 荷主 ID は UUID（36 文字）なので、理由の欄に収まる必要がある——
        // 収まらないと記録だけが落ち、操作は通ったのに 500 が返る（実測）。
        String shipperId = java.util.UUID.randomUUID().toString();

        assertThat(link("shipper01", Map.of("shipperId", shipperId), "ROLE_ADMIN")
                .getStatusCode())
                .as("識別子が列に収まらないと、ここが 500 になる")
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject(
                "SELECT reason FROM auth_audit_log WHERE username = ? AND event_type = ?",
                String.class, "shipper01", "SHIPPER_LINKED"))
                .as("どの荷主に紐付けたかが読めなければ、監査の意味が無い")
                .isEqualTo(shipperId);
    }

    @Test
    @DisplayName("紐付けを外したことも監査ログに残る")
    void recordsTheUnlink() {
        link("shipper01", Map.of("shipperId", "SHP-000001"), "ROLE_ADMIN");
        link("shipper01", Map.of(), "ROLE_ADMIN");

        assertThat(jdbc.queryForList(
                "SELECT event_type FROM auth_audit_log WHERE username = ?",
                String.class, "shipper01"))
                .contains("SHIPPER_LINKED", "SHIPPER_UNLINKED");
    }
}
