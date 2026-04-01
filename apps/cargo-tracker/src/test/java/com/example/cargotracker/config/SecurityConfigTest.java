package com.example.cargotracker.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Nested
    @DisplayName("未認証アクセス")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("ルートURLへのアクセスはログイン画面にリダイレクトされる")
        void rootRedirectsToLogin() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("ログイン画面は認証なしでアクセスできる")
        void loginPageIsPublic() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("静的リソース（webjars）は認証なしでアクセスできる")
        void webJarsArePublic() throws Exception {
            mockMvc.perform(get("/webjars/bootstrap/css/bootstrap.min.css"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Swagger UI は認証なしでアクセスできる")
        void swaggerUiIsPublic() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OpenAPI JSON は認証なしでアクセスできる")
        void openApiDocsArePublic() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"));
        }
    }

    @Nested
    @DisplayName("フォームログイン")
    class FormLogin {

        @Test
        @DisplayName("正しい認証情報でログインするとホームにリダイレクトされる")
        void loginWithValidCredentialsRedirectsToHome() throws Exception {
            mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        }

        @Test
        @DisplayName("誤った認証情報でログインするとエラーにリダイレクトされる")
        void loginWithInvalidCredentialsRedirectsToError() throws Exception {
            mockMvc.perform(formLogin("/login").user("admin").password("wrong"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }
    }

    @Nested
    @DisplayName("認証済みアクセス")
    class AuthenticatedAccess {

        @Test
        @WithMockUser(username = "admin", roles = "USER")
        @DisplayName("認証済みユーザーはホームにアクセスできる")
        void authenticatedUserCanAccessHome() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "admin", roles = "USER")
        @DisplayName("ログアウト後はログイン画面にリダイレクトされる")
        void logoutRedirectsToLogin() throws Exception {
            mockMvc.perform(logout("/logout"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?logout"));
        }
    }
}
