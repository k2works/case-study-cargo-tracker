package com.example.cargotracker.gateway.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT を検証し、利用者とロールを後段サービスへ伝える。
 *
 * <p>検証はここ 1 か所だけで行う（ADR-0001 決定 4 の分担）。後段でも検証すると、
 * どちらが正かが曖昧になり、片方を直したときにもう片方が置き去りになる。</p>
 *
 * <p>公開経路（追跡照会・ログイン・ヘルスチェック）は認証を求めない。ここを一律に
 * 守ると、荷受人が使えなくなり、ヘルスチェックまで 401 になって再起動ループに入る。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 認証を求めない経路。増減はここだけで行い、検査もこの表から回す。 */
    public static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/tracking/public/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info");

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final SecretKey key;

    public JwtAuthenticationFilter(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (isPublic(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(header.substring("Bearer ".length()))
                    .getPayload();

            // 後段サービスはこのヘッダを信じる。だからこのフィルタを通らない経路を作らない。
            request.setAttribute("cargo-tracker.username", claims.getSubject());
            request.setAttribute("cargo-tracker.roles", claims.get("roles"));
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            unauthorized(response);
        }
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        // 理由は返さない。入力仕様を教えない（認可は入力検証より先）。
        response.getWriter().write("{\"code\":\"UNAUTHENTICATED\"}");
    }
}
