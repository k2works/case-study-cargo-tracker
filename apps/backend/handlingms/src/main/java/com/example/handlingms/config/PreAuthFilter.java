package com.example.handlingms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * gatewayms 経由の認証情報を Spring Security の {@link org.springframework.security.core.Authentication}
 * に変換するフィルタ（IT10 A1.4 / US30 / IT9 H3 解消）。
 *
 * <p>4 ms 目の実装（billingms / routingms / bookingms と同パターン）。IT11 で
 * {@code shared-security} モジュール抽出（ADR-0022 起票候補、IT9 M1）。</p>
 */
public class PreAuthFilter extends OncePerRequestFilter {

    static final String HEADER_USER = "X-Forwarded-User";
    static final String HEADER_ROLE = "X-Forwarded-Role";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Logger log = LoggerFactory.getLogger(PreAuthFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String user = request.getHeader(HEADER_USER);
        String roleHeader = request.getHeader(HEADER_ROLE);

        if (user != null && !user.isBlank() && roleHeader != null && !roleHeader.isBlank()) {
            List<GrantedAuthority> authorities = parseAuthorities(roleHeader);
            AbstractAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, "N/A", authorities);
            authentication.setDetails(request.getRequestURI());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("PreAuth 認証成功: user={}, roles={}", user, authorities);
        }

        chain.doFilter(request, response);
    }

    private static List<GrantedAuthority> parseAuthorities(String header) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String raw : header.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String roleName = trimmed.startsWith(ROLE_PREFIX) ? trimmed : ROLE_PREFIX + trimmed;
            authorities.add(new SimpleGrantedAuthority(roleName));
        }
        return authorities;
    }
}
