package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.TrackingCommandService;
import com.example.trackingms.application.TrackingQueryService;
import com.example.trackingms.domain.services.TrackingTokenService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * TrackingController のメソッド認可テスト（IT10 A1.1 / US30）。
 *
 * <p>{@code @PreAuthorize("hasAnyRole('TRACKER', 'ADMIN')")} が
 * HerokuSecurityConfig の URL ルール認可と二段重層で機能することを slice テストで検証する。</p>
 *
 * <p>公開照会 endpoint（{@link PublicTrackingController}）は JWT 時限トークン代替認証で
 * permitAll のため対象外。</p>
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = TrackingController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        })
@Import(TrackingControllerAuthorizationTest.TestMethodSecurityConfig.class)
class TrackingControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingCommandService commandService;

    @MockitoBean
    private TrackingQueryService queryService;

    @MockitoBean
    private TrackingTokenService tokenService;

    @Test
    @DisplayName("未認証の GET は 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tracking/TN-001"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @DisplayName("ROLE_SHIPPER（権限不一致）の GET は 403 Forbidden（@PreAuthorize 違反）")
    @WithMockUser(roles = "SHIPPER")
    void shouldReturn403WhenWrongRole() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tracking/TN-001"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_TRACKER の GET は @PreAuthorize を通過し業務応答（404）を返す")
    @WithMockUser(roles = "TRACKER")
    void shouldPassPreAuthorizeWhenTrackerRole() throws Exception {
        // queryService がデフォルト null を返すため業務的に 404 になるが、
        // ここでは @PreAuthorize が 401/403 を返さず Controller を実行することを検証する。
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tracking/TN-001"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    /**
     * @WebMvcTest で {@code @PreAuthorize} を有効化するためのテスト専用 SecurityConfig。
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
