package com.example.cargotracker.quote.interfaces.web;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 見積作成フローの E2E テスト。
 * 実際の Spring Security（フォームログイン）を通したハッピーパスを検証する。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("見積作成フロー E2E テスト")
class QuoteWebE2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ── 未認証アクセス ─────────────────────────────────────────────────────

    @Test
    @DisplayName("未認証で見積一覧にアクセスするとログイン画面にリダイレクトされる")
    void 未認証_見積一覧はログインへリダイレクト() throws Exception {
        mockMvc.perform(get("/quotes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("未認証で見積作成フォームにアクセスするとログイン画面にリダイレクトされる")
    void 未認証_見積フォームはログインへリダイレクト() throws Exception {
        mockMvc.perform(get("/quotes/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── ログイン → 見積作成 ハッピーパス ────────────────────────────────────

    @Test
    @DisplayName("ログイン後に見積登録フォームにアクセスできる")
    void ログイン後_見積登録フォームにアクセスできる() throws Exception {
        // Step 1: フォームログイン
        var session = mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();

        // Step 2: セッションを使って見積登録フォームにアクセス
        mockMvc.perform(get("/quotes/new").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("見積登録")))
                .andExpect(content().string(containsString("出発地")))
                .andExpect(content().string(containsString("目的地")))
                .andExpect(content().string(containsString("希望着日")))
                .andExpect(content().string(containsString("貨物種別")))
                .andExpect(content().string(containsString("重量")));
    }

    @Test
    @DisplayName("見積作成フォームを送信すると見積詳細ページにリダイレクトされる")
    void 見積フォーム送信_詳細ページへリダイレクト() throws Exception {
        // Step 1: フォームログイン
        var session = mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();

        // Step 2: 見積フォーム送信
        mockMvc.perform(post("/quotes")
                        .session((MockHttpSession) session)
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "USNYC")
                        .param("requestedArrivalDate", "2025-12-01")
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "1000.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/quotes/*"));
    }

    @Test
    @DisplayName("見積詳細ページにはルート候補・経由港・所要日数・概算料金が表示される")
    void 見積詳細ページ_ルート候補が表示される() throws Exception {
        // Step 1: ログイン
        var session = mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();

        // Step 2: 見積作成してリダイレクト先を取得
        var redirectUrl = mockMvc.perform(post("/quotes")
                        .session((MockHttpSession) session)
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "USNYC")
                        .param("requestedArrivalDate", "2025-12-01")
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "1000.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        // Step 3: 詳細ページを取得し内容を確認
        mockMvc.perform(get(redirectUrl).session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("見積詳細")))
                .andExpect(content().string(containsString("ルート候補")))
                .andExpect(content().string(containsString("所要日数")))
                .andExpect(content().string(containsString("概算料金")));
    }

    @Test
    @DisplayName("見積一覧ページにアクセスできる")
    void 見積一覧ページにアクセスできる() throws Exception {
        // Step 1: ログイン
        var session = mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();

        // Step 2: 見積一覧にアクセス
        mockMvc.perform(get("/quotes").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("見積")));
    }
}
