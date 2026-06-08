package com.example.bookingms.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PreAuthFilter の単体テスト（IT10 A1.4 / US30 / IT9 H3）。
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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/booking-1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "SALES");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SALES");
    }

    @Test
    @DisplayName("X-Forwarded-Role がカンマ区切りなら複数 Authority に展開される")
    void shouldSplitMultipleRoles() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/booking-1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "SALES, ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SALES", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("既に ROLE_ プレフィックス付きの値はそのまま Authority に変換される")
    void shouldKeepExistingRolePrefix() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/booking-1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "ROLE_SALES");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SALES");
    }

    @Test
    @DisplayName("X-Forwarded-User が無ければ Authentication は構築されない")
    void shouldNotBuildAuthenticationWhenUserMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/booking-1");
        request.addHeader(PreAuthFilter.HEADER_ROLE, "SALES");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("X-Forwarded-Role が無ければ Authentication は構築されない")
    void shouldNotBuildAuthenticationWhenRoleMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/booking-1");
        request.addHeader(PreAuthFilter.HEADER_USER, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("ヘッダなしでも次のフィルタを呼び出す")
    void shouldDelegateToNextFilterEvenWithoutHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
