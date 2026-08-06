package com.example.cargotracker.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;

/**
 * 開発環境のログイン情報の事前入力（{@code app.demo-login}）を検証する。
 *
 * <p><strong>既定は無効である。</strong> 有効化していない環境で認証情報が画面に入っていたら、
 * それは事故である。設定を足したときだけ入ることを、既定値の側から固定する。
 *
 * <p>本クラスは既定（無効）を検証する。有効時の挙動は {@link DemoLoginPrefillEnabledTest} が担う。
 */
@AutoConfigureMockMvc
class DemoLoginPrefillTest extends PostgreSQLIntegrationTestBase {

    @Test
    void 既定ではログイン情報が事前入力されない() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"sales\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("password\" value=\""))));
    }

    @Test
    void 既定では開発環境向けの注意書きが表示されない() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("開発環境"))));
    }

    @Test
    void テンプレートのコメントがブラウザに配信されない() throws Exception {
        // 認可やロックの設計意図をコメントで書いている。**それを画面のソースに載せない。**
        // 「ロック中と誤りで同じメッセージを返す」理由を配信するのは、
        // 攻撃者に判断材料を渡すのと同じである
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("<!--"))));
    }

    /** 有効化したときにだけ事前入力される。 */
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
        "app.demo-login.enabled=true",
        "app.demo-login.username=sales",
        "app.demo-login.password=password",
    })
    static class DemoLoginPrefillEnabledTest extends PostgreSQLIntegrationTestBase {

        @Test
        void 有効にすると利用者IDとパスワードが入力済みになる() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("value=\"sales\"")))
                    .andExpect(content().string(Matchers.containsString("value=\"password\"")));
        }

        @Test
        void 有効にすると開発環境である旨の注意書きが表示される() throws Exception {
            // 事前入力されていることを利用者に隠さない。
            // 気づかないまま本番同様の画面だと思われるのが最も危ない
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("開発環境")));
        }
    }
}
