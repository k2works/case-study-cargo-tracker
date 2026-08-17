package com.example.cargotracker.demo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 自動実行デモを<strong>無効にしたら本当に届かない</strong>ことを確かめる。
 *
 * <p><strong>安全装置は「入れたこと」ではなく「働くこと」を確かめる。</strong>
 * この画面は認証の外（{@code /public/**}）にあり、有効なままなら
 * <strong>誰でも荷主と予約を作れる</strong>。無効化が効いていないことに気づける形が要る。
 *
 * <p><strong>ボタンも同時に消える。</strong> 入口だけ塞いでボタンが残ると、
 * 押した利用者は 404 に落ちる。
 *
 * <p><strong>コンテキストが 1 つ増える。</strong> 設定を変えると Spring は別の
 * コンテキストを作る（{@code PostgreSQLIntegrationTestBase} の注意書き）。
 * それでもここは分ける —— <strong>無効化を確かめる手段が他に無い</strong>。
 */
@TestPropertySource(properties = "cargo-tracker.demo.autopilot.enabled=false")
class DemoAutopilotDisabledTest extends PostgreSQLIntegrationTestBase {

    @Test
    void 無効なら開始できない() throws Exception {
        mockMvc.perform(post("/public/demo/start").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 無効なら片付けもできない() throws Exception {
        mockMvc.perform(post("/public/demo/reset").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 無効ならログイン画面にボタンが出ない() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("/public/demo/start"))));
    }
}
