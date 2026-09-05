package com.example.cargotracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/**
 * 動作確認用の利用者が実際に使えることを確かめる（ADR-0004）。
 *
 * <p>画面に一覧を出すだけでは足りない。パスワードのハッシュが違っていても、
 * 認証の失敗は理由を区別しないので「利用者名またはパスワードが正しくありません」
 * としか出ず、原因は画面から分からない。ここで実際にログインまで通す。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "cargo-tracker.demo-users=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoUserSeedIT extends AbstractAxonIntegrationTest {

    /** 一覧の利用者すべてで共通のパスワード（画面にもそう出す）。 */
    private static final String SHARED_PASSWORD = "secret1234";

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private HttpStatus signIn(String username, String password) {
        return (HttpStatus) rest.post()
                .uri("http://localhost:" + port + "/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
    }

    @Test
    @DisplayName("有効にすると動作確認用の利用者が入る")
    void seedsDemoUsers() {
        Integer count = new JdbcTemplate(dataSource)
                .queryForObject("SELECT count(*) FROM users WHERE username = 'sales01'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("画面に出す共通パスワードで実際にログインできる")
    void signsInWithTheSharedPassword() {
        assertThat(signIn("sales01", SHARED_PASSWORD)).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("無効化された利用者はログインできない")
    void rejectsTheDisabledAccount() {
        // 一覧に載せる以上、実際に落ちることまで確かめられなければ確認の役に立たない。
        assertThat(signIn("disabled01", SHARED_PASSWORD)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
