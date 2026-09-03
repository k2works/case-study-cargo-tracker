package com.example.cargotracker.gateway.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
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

    /** 後段へ利用者とロールを伝えるヘッダ。後段は署名を再検証せず、これを信じる。 */
    public static final String USERNAME_HEADER = "X-Auth-Username";
    public static final String ROLES_HEADER = "X-Auth-Roles";
    public static final String SHIPPER_ID_HEADER = "X-Auth-Shipper-Id";

    private static final List<String> IDENTITY_HEADERS =
            List.of(USERNAME_HEADER, ROLES_HEADER, SHIPPER_ID_HEADER);

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
            //
            // リクエスト属性ではなくヘッダに載せる。属性は Gateway の JVM 内に閉じて
            // おり、後段へ HTTP 転送されない。属性に置いたままだと、後段は利用者も
            // ロールも受け取れないのに「伝えている」と読めるコードが残る。
            chain.doFilter(withIdentity(request, claims), response);
        } catch (JwtException | IllegalArgumentException e) {
            unauthorized(response);
        }
    }

    /**
     * 利用者とロールをヘッダに載せた要求を返す。
     *
     * <p>外から来た同名のヘッダは必ず上書きする。上書きしないと、利用者が自分で
     * {@code X-Auth-Roles: ROLE_ADMIN} を付けるだけで権限を偽れる。</p>
     */
    private static HttpServletRequest withIdentity(HttpServletRequest request, Claims claims) {
        String username = claims.getSubject();
        Object roles = claims.get("roles");
        String rolesHeader = roles instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.joining(","))
                : "";
        Object shipperId = claims.get("shipperId");

        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (USERNAME_HEADER.equalsIgnoreCase(name)) {
                    return username;
                }
                if (ROLES_HEADER.equalsIgnoreCase(name)) {
                    return rolesHeader;
                }
                if (SHIPPER_ID_HEADER.equalsIgnoreCase(name)) {
                    return shipperId == null ? null : String.valueOf(shipperId);
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                String value = getHeader(name);
                if (IDENTITY_HEADERS.stream().anyMatch(h -> h.equalsIgnoreCase(name))) {
                    return value == null
                            ? Collections.emptyEnumeration()
                            : Collections.enumeration(List.of(value));
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
                IDENTITY_HEADERS.forEach(h -> {
                    names.removeIf(existing -> existing.equalsIgnoreCase(h));
                    if (getHeader(h) != null) {
                        names.add(h);
                    }
                });
                return Collections.enumeration(names);
            }
        };
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        // 理由は返さない。入力仕様を教えない（認可は入力検証より先）。
        response.getWriter().write("{\"code\":\"UNAUTHENTICATED\"}");
    }
}
