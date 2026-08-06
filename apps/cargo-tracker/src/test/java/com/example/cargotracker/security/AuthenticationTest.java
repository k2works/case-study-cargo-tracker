package com.example.cargotracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
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
        // **ログインしてから始める。** 未認証のまま logout を呼んで unauthenticated() を
        // 検証しても、最初から未認証なので破棄が壊れていても必ず緑になる
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user("sales").password("password"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?logout"));

        assertThat(session.isInvalid()).as("セッションが破棄されていること").isTrue();
    }

    @Test
    void ログアウト後は同じセッションで業務画面に戻れない() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user("sales").password("password"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(post("/logout").session(session).with(csrf()));

        mockMvc.perform(get("/shippers").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 業務画面はブラウザにキャッシュされない() throws Exception {
        // US27「ブラウザバックで業務画面に戻れない」の担保。
        // SecurityConfig の cacheControl は既定を維持するだけの記述であり、
        // **実際にヘッダが付くことを確かめなければ「入れたつもり」で終わる**
        mockMvc.perform(get("/shippers").with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void シードされた利用者の権限で認可が働く() throws Exception {
        // @WithMockUser はロールをその場で組み立てるため、DB から読んだ権限が
        // hasRole に届いているかを一度も通らない。ここが壊れると全ロールが権限を失う
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user("sales").password("password"))
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/shippers").session(session)).andExpect(status().isOk());
    }

    @Test
    void 存在しない利用者IDでも同一のメッセージを返す() throws Exception {
        // 出し分けると、その ID が実在するかを第三者に教えることになる
        mockMvc.perform(formLogin("/login").user("no-such-user").password("password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 権限不足のときは専用画面へ転送される() throws Exception {
        // Whitelabel Error Page（英語・status=403）を見せると、利用者は障害だと受け取る。
        // **MockMvc は forward 先を描画しない**ため、ここでは転送先だけを固定し、
        // 画面の中身は次のテストで直接確認する
        mockMvc.perform(get("/shippers"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));
    }

    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 権限不足の画面は日本語の説明とダッシュボードへの導線を持つ() throws Exception {
        // マニュアル（付録 B Q2-2）が案内している文言を実際に出す。
        // 行き止まりを作らないため戻り先も置く
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("アクセスが拒否されました")))
                .andExpect(content().string(containsString("ダッシュボードに戻る")));
    }
}
