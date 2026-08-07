package com.example.cargotracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * ロックされたアカウントの解除（US33）。
 *
 * <p><strong>ロックの自動解除を待つ間、現場は止まる。</strong> 輸送は待ってくれないため、
 * 待ち時間がそのまま業務の停止時間になる。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class AccountUnlockTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    /** ロック中の利用者を 1 人用意する。 */
    private String ロック中の利用者(int failedAttempts) {
        String username = "locked-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO users (username, email, password, enabled,
                                   failed_attempts, locked_until)
                VALUES (?, ?, '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe',
                        TRUE, ?, ?)
                """,
                username, username + "@example.com", failedAttempts,
                java.sql.Timestamp.from(clock.instant().plus(30, ChronoUnit.MINUTES)));
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role) "
                        + "SELECT id, 'ROLE_HANDLER' FROM users WHERE username = ?", username);
        return username;
    }

    private Instant ロック期限(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_until FROM users WHERE username = ?", Instant.class, username);
    }

    private int 失敗回数(String username) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM users WHERE username = ?", Integer.class, username);
        return value == null ? 0 : value;
    }

    /** 受入基準: ロック中のアカウント一覧を、ロック日時・失敗回数とともに確認できる。 */
    @Test
    void ロック中のアカウントを一覧で確認できる() throws Exception {
        String username = ロック中の利用者(5);

        mockMvc.perform(get("/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(username)))
                .andExpect(content().string(Matchers.containsString("5")));
    }

    /** 受入基準: 解除でき、<strong>解除と同時に失敗回数がリセットされる</strong>。 */
    @Test
    void 解除すると失敗回数もリセットされる() throws Exception {
        String username = ロック中の利用者(5);

        mockMvc.perform(post("/admin/accounts/{username}/unlock", username)
                        .param("reason", "本人確認のうえ解除")
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts"));

        assertThat(ロック期限(username)).isNull();
        assertThat(失敗回数(username)).isZero();
    }

    /** 受入基準: <strong>解除には理由の入力が必須である。</strong> */
    @Test
    void 理由が無ければ解除できない() throws Exception {
        String username = ロック中の利用者(5);

        mockMvc.perform(post("/admin/accounts/{username}/unlock", username)
                        .param("reason", "   ")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("理由")));

        // **解除されていない。** 理由が無いまま解除が通ると、監査ログが用を成さない
        assertThat(ロック期限(username)).isNotNull();
    }

    /** 存在しない利用者を解除しようとしても 500 にしない。 */
    @Test
    void 存在しない利用者の解除は404になる() throws Exception {
        mockMvc.perform(post("/admin/accounts/{username}/unlock", "no-such-user")
                        .param("reason", "本人確認のうえ解除")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>ロック期限が切れた利用者は一覧に出ない。</strong>
     *
     * <p>すでに自動解除されており、解除する対象ではない。並べると管理者が
     * 「どれを解除すべきか」を自分で判断することになる。
     */
    @Test
    void 期限切れのロックは一覧に出ない() throws Exception {
        String username = "expired-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO users (username, email, password, enabled,
                                   failed_attempts, locked_until)
                VALUES (?, ?, '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe',
                        TRUE, 5, ?)
                """,
                username, username + "@example.com",
                java.sql.Timestamp.from(clock.instant().minus(1, ChronoUnit.HOURS)));

        mockMvc.perform(get("/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString(username))));
    }

    /** ロックされていない利用者は一覧に出ない。**解除する対象ではない。** */
    @Test
    void ロックされていない利用者は一覧に出ない() throws Exception {
        mockMvc.perform(get("/admin/accounts"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("sales"))));
    }

    /** 受入基準: <strong>管理者以外はこの操作に到達できない。</strong> */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 管理者以外は一覧を開けない() throws Exception {
        mockMvc.perform(get("/admin/accounts"))
                .andExpect(status().isForbidden());
    }

    /** 管理者以外は解除も実行できない。**書き込みの入口も守る。** */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 管理者以外は解除を実行できない() throws Exception {
        String username = ロック中の利用者(5);

        mockMvc.perform(post("/admin/accounts/{username}/unlock", username)
                        .param("reason", "解除したい")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(ロック期限(username)).isNotNull();
    }
}
