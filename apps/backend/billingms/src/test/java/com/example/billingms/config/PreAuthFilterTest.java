package com.example.billingms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PreAuthFilter の単体テスト（IT10 A1.4 / US30 / IT9 H3）。
 *
 * <p>{@code X-Forwarded-User} / {@code X-Forwarded-Role} ヘッダから
 * {@code Authentication} を構築し SecurityContext に設定する挙動を検証する。</p>
 */
class PreAuthFilterTest {

    private final PreAuthFilter filter = new PreAuthFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("X-Forwarded-User と X-Forwarded-Role が揃えば Authentication が構築される")
    void shouldBuildAuthenticationWhenBothHeadersPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/billing/invoices/1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "ACCOUNTANT");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ACCOUNTANT");
    }

    @Test
    @DisplayName("X-Forwarded-Role がカンマ区切りなら複数 Authority に展開される")
    void shouldSplitMultipleRoles() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/billing/invoices/1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "ACCOUNTANT, ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ACCOUNTANT", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("既に ROLE_ プレフィックス付きの値はそのまま Authority に変換される")
    void shouldKeepExistingRolePrefix() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/billing/invoices/1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "ROLE_ACCOUNTANT");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ACCOUNTANT");
    }

    @Test
    @DisplayName("X-Forwarded-User が無ければ Authentication は構築されない（SecurityContext は空）")
    void shouldNotBuildAuthenticationWhenUserMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/billing/invoices/1");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "ACCOUNTANT");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("X-Forwarded-Role が無ければ Authentication は構築されない（SecurityContext は空）")
    void shouldNotBuildAuthenticationWhenRoleMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/billing/invoices/1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("ヘッダなし（ローカル開発相当）でも次のフィルタを呼び出す")
    void shouldDelegateToNextFilterEvenWithoutHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // FilterChain が呼ばれて Response が完了している（status は default 200）
        assertThat(response.getStatus()).isEqualTo(200);
        // 後続フィルタに渡ったことの間接検証として、SecurityContext は空のまま
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
