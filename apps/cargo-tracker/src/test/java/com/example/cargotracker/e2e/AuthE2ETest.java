package com.example.cargotracker.e2e;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証フロー API E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("認証フロー API E2E テスト")
class AuthE2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("E01 正しい認証情報でログインできる")
    void e01_login_shouldSucceed() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"));
    }

    @Test
    @DisplayName("E01 誤った認証情報でログインが拒否される")
    void e01_loginWithWrongPassword_shouldFail() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?error"));
    }

    @Test
    @DisplayName("E01 ログアウトできる")
    void e01_logout_shouldSucceed() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?logout"));
    }

    @Test
    @DisplayName("E01 未認証ユーザーは保護ページにアクセスするとログインにリダイレクトされる")
    void e01_unauthenticated_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("E01 認証済みユーザーはホームページにアクセスできる")
    void e01_authenticated_shouldAccessHomePage() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("E01 ヘルスチェックは認証なしでアクセスできる")
    void e01_healthCheck_shouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
