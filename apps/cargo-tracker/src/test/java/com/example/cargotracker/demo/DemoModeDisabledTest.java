package com.example.cargotracker.demo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * デモモードを<strong>無効にしたら本当に届かない</strong>ことを確かめる。
 *
 * <p><strong>安全装置は「入れたこと」ではなく「働くこと」を確かめる。</strong>
 * 開始の入口は認証の外（{@code /public/**}）にあり、有効なままなら
 * <strong>誰でも荷主と予約を作り続けられる</strong>。無効化が効いていないことに
 * 気づける形が要る。
 *
 * <p><strong>ボタンも帯も同時に消える。</strong> 入口だけ塞いでボタンが残ると、
 * 押した利用者は 404 に落ちる。
 *
 * <p><strong>コンテキストが 1 つ増える。</strong> 設定を変えると Spring は別の
 * コンテキストを作る（{@code PostgreSQLIntegrationTestBase} の注意書き）。
 * それでもここは分ける —— <strong>無効化を確かめる手段が他に無い</strong>。
 *
 * <p><strong>増やしたぶんは自分で返す。</strong> コンテキストが増えるたびに
 * HikariCP のプールがもう 1 セット張られ、PostgreSQL の {@code max_connections} を
 * 超えて<strong>無関係な検査が「too many clients」で落ちる</strong>（実際に落とした）。
 * {@code @DirtiesContext} で終わったら閉じ、接続を返す。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "cargo-tracker.demo.mode.enabled=false")
class DemoModeDisabledTest extends PostgreSQLIntegrationTestBase {

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
    void 無効なら状況も読めない() throws Exception {
        mockMvc.perform(get("/public/demo/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 無効ならログイン画面にボタンが出ない() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/public/demo/start"))));
    }

    /**
     * <strong>業務画面にも帯を出さない。</strong> 無効なのに帯が出ていると、
     * 押しても何も起きないボタンを置くことになる。
     */
    @Test
    void 無効なら業務画面に帯が出ない() throws Exception {
        mockMvc.perform(get("/").with(user("demo").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("demo-mode-banner"))));
    }
}
