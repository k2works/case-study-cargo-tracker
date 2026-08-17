package com.example.cargotracker.demo;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * デモモードの入口が<strong>認証の外にあること</strong>を確かめる。
 *
 * <p><strong>ログイン画面のボタンから始める以上、認証を要求してはならない。</strong>
 * 認証が要る場所に置くと、ボタンを押した先でログインを求められる —— <strong>まだ
 * ログインの仕方を知らない人のための導線が、ログインを前提にしてしまう</strong>。
 *
 * <p>この検査は<strong>ログイン画面にボタンが出ること</strong>も同時に守る。
 * 入口が動いてもボタンが無ければ、誰も到達できない（IT7 で公開追跡が同じ形だった）。
 */
class DemoModeAccessTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private DemoModeService demoMode;

    @AfterEach
    void stopDemoMode() {
        demoMode.stop();
    }

    @Test
    void ログイン画面から認証なしでデモモードを始められる() throws Exception {
        mockMvc.perform(post("/public/demo/start").with(csrf()))
                .andExpect(status().is3xxRedirection());

        org.assertj.core.api.Assertions.assertThat(demoMode.running())
                .as("押したら動きだす")
                .isTrue();
    }

    /**
     * <strong>入口があってもボタンが無ければ到達できない。</strong>
     * ロール別の到達性は認証済みの利用者にしか働かず、
     * 未認証の利用者にとっての作業入口はログイン画面だけである。
     */
    @Test
    void ログイン画面に開始のボタンが出る() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/public/demo/start")))
                .andExpect(content().string(containsString("デモモードを開始する")));
    }

    /**
     * <strong>動かしたあとのログイン画面では、開始でなく停止を出す。</strong>
     * 動いているのに「開始する」と出ていると、押さなければ始まらないと思わせる。
     */
    @Test
    void 動いているときのログイン画面には停止が出る() throws Exception {
        demoMode.start();

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/public/demo/stop")))
                .andExpect(content().string(containsString("実行中")));
    }

    /** 状況は認証なしで読める（帯が読みに来る）。 */
    @Test
    void 状況を認証なしで読める() throws Exception {
        mockMvc.perform(get("/public/demo/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"running\"")));
    }
}
