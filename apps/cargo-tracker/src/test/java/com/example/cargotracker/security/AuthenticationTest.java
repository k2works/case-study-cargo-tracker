package com.example.cargotracker.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * US26: システムにログインする / US27: システムからログアウトする。
 *
 * <p>受け入れ基準（{@code docs/requirements/user_story.md}）に 1:1 で対応させる。
 */
@AutoConfigureMockMvc
class AuthenticationTest extends PostgreSQLIntegrationTestBase {

    @Test
    void 利用者IDとパスワードでログインできる() throws Exception {
        mockMvc.perform(formLogin("/login").user("sales").password("password"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 認証情報が一致しない場合はログイン画面にエラー付きで戻る() throws Exception {
        mockMvc.perform(formLogin("/login").user("sales").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 未ログインで業務機能にアクセスするとログイン画面へ誘導される() throws Exception {
        mockMvc.perform(get("/shippers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 公開追跡は未ログインでもアクセスできる() throws Exception {
        // US18 の公開追跡は認証不要（ui_design.md）
        mockMvc.perform(get("/public/tracking")).andExpect(status().isOk());
    }

    @Test
    void ヘルスチェックは未ログインでもアクセスできる() throws Exception {
        // 過負荷時に liveness が 401/503 を返すと ECS が再起動ループに入る
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 権限のない画面にアクセスすると403になる() throws Exception {
        // 荷主管理は ROLE_SALES のみ（ui_design.md のナビゲーション構成）
        mockMvc.perform(get("/shippers")).andExpect(status().isForbidden());
    }

    @Test
    void ログアウトするとセッションが破棄されログイン画面に戻る() throws Exception {
        mockMvc.perform(logout("/logout"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?logout"));
    }
}
