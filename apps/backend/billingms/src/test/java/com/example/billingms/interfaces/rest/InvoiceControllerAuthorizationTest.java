package com.example.billingms.interfaces.rest;

import com.example.billingms.application.InvoiceQueryService;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;

/**
 * InvoiceController のメソッド認可テスト（IT10 A1.1 / US30）。
 *
 * <p>{@code @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")} が
 * HerokuSecurityConfig の URL ルール認可と二段重層で機能することを slice テストで検証する。</p>
 *
 * <p>テスト方針: {@link TestMethodSecurityConfig} で {@code @EnableMethodSecurity} を有効化し、
 * 既定の SecurityConfig（permitAll）の代わりに認証必須の SecurityFilterChain を inject する。
 * これにより heroku profile 相当の認可挙動を slice テストで再現する（IT9 H4 への先行対応）。</p>
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = InvoiceController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
@Import(InvoiceControllerAuthorizationTest.TestMethodSecurityConfig.class)
class InvoiceControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandGateway commandGateway;

    @MockitoBean
    private InvoiceQueryService queryService;

    @MockitoBean
    private Clock clock;

    @Test
    @DisplayName("未認証の GET は 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/billing/invoices/inv-1"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @DisplayName("ROLE_SHIPPER（権限不一致）の GET は 403 Forbidden（@PreAuthorize 違反）")
    @WithMockUser(roles = "SHIPPER")
    void shouldReturn403WhenWrongRole() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/billing/invoices/inv-1"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ACCOUNTANT の GET は @PreAuthorize を通過し業務応答（404）を返す")
    @WithMockUser(roles = "ACCOUNTANT")
    void shouldPassPreAuthorizeWhenAccountantRole() throws Exception {
        // queryService がデフォルト null を返すため業務的に 404 になるが、
        // ここでは @PreAuthorize が 401/403 を返さず Controller を実行することを検証する。
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/billing/invoices/inv-1"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    /**
     * @WebMvcTest で {@code @PreAuthorize} を有効化するためのテスト専用 SecurityConfig。
     *
     * <p>本番の HerokuSecurityConfig は {@code @Profile("heroku")} のため slice テストで
     * 直接 inject できない。同等の {@code @EnableMethodSecurity} + 認証必須挙動を再現する。</p>
     */
    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestMethodSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }
    }
}
