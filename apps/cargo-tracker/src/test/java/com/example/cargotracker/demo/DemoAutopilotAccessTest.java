package com.example.cargotracker.demo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;

/**
 * 自動実行デモの入口が<strong>認証の外にあること</strong>を確かめる。
 *
 * <p><strong>ログイン画面のボタンから始める以上、認証を要求してはならない。</strong>
 * 認証が要る場所に置くと、ボタンを押した先でログインを求められる —— <strong>まだ
 * ログインの仕方を知らない人のための導線が、ログインを前提にしてしまう</strong>。
 *
 * <p>この検査は<strong>ログイン画面にボタンが出ること</strong>も同時に守る。
 * 入口が動いてもボタンが無ければ、誰も到達できない（IT7 で公開追跡が同じ形だった）。
 */
class DemoAutopilotAccessTest extends PostgreSQLIntegrationTestBase {

    @Test
    void ログイン画面から認証なしでデモを始められる() throws Exception {
        mockMvc.perform(post("/public/demo/start").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/public/demo/*"));
    }

    /**
     * <strong>入口があってもボタンが無ければ到達できない。</strong>
     * ロール別の到達性は認証済みの利用者にしか働かず、
     * 未認証の利用者にとっての作業入口はログイン画面だけである。
     */
    @Test
    void ログイン画面にデモ開始のボタンが出る() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("/public/demo/start")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("デモを開始する")));
    }

    /** 知らない実行 ID は 404 になる（実行中の別の画面を覗けない）。 */
    @Test
    void 知らない実行は見つからない() throws Exception {
        mockMvc.perform(get("/public/demo/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
