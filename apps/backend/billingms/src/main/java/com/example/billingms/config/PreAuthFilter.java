package com.example.billingms.config;

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
 * <p>gatewayms の {@code JwtAuthenticationFilter} が JWT 検証成功時に
 * {@code X-Forwarded-User} / {@code X-Forwarded-Role} ヘッダを付与する。本フィルタは両ヘッダを
 * 読み取って {@link UsernamePasswordAuthenticationToken} を構築し、SecurityContext に設定する。</p>
 *
 * <p><strong>IT9 H3 解消の理由:</strong> 従来は heroku profile で {@code httpBasic} 認証も
 * 有効だったため、gatewayms をバイパスして各 ms に直接アクセスされた場合に BASIC 認証で突破される
 * リスクがあった。本フィルタ導入と同時に {@code HerokuSecurityConfig} で {@code httpBasic} を
 * 無効化することで、JWT 経由でない直接アクセスは 401 で拒否される深層防御を確立する。</p>
 *
 * <p><strong>ロール命名規約:</strong> JWT claim {@code role} はプレーン（例: {@code ACCOUNTANT}）
 * であり、Spring Security の {@code hasRole('ACCOUNTANT')} と整合させるため、本フィルタで
 * {@code ROLE_} プレフィックスを付与して {@link GrantedAuthority} を構築する。複数ロールは
 * カンマ区切りで {@code X-Forwarded-Role} に乗せる前提（gatewayms 側で集約済み）。</p>
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
