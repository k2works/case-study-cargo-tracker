package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.services.TrackingTokenInvalidException;
import com.example.trackingms.domain.services.TrackingTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公開追跡照会エンドポイント {@code /api/v1/public/tracking/{tn}} 向けの JWT 検証フィルタ
 * （US18 / ADR-0013、IT8 H2 持ち越し T1.5 で SecurityFilterChain 統合済み）。
 *
 * <p>本フィルタは IT8 T1.5 以降、{@code SecurityConfig.publicTrackingFilterChain} 内で
 * {@link org.springframework.security.web.access.intercept.AuthorizationFilter} の前に挿入される。
 * Spring Security の標準認証フローはバイパスし、JWT 検証失敗時に
 * <strong>HTTP 403 Forbidden + Problem Detail JSON</strong> を返す（ui_design.md L738 準拠）。
 * リソース存在の秘匿のため、401 ではなく 403 を採用する。</p>
 *
 * <p>検証ロジックは {@link TrackingTokenService#verify(String, TrackingNumber)} に委譲する。
 * IT9 T1.4「全サービス Spring Security 統一」で、本フィルタ以外の trackingms endpoint も
 * SecurityFilterChain 経由の認証へ移行する予定。</p>
 */
public class PublicTrackingTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicTrackingTokenFilter.class);
    private static final Pattern PATH_PATTERN =
            Pattern.compile("^/api/v1/public/tracking/(TRK-[A-Z0-9]{10})(/.*)?$");

    private final TrackingTokenService tokenService;

    public PublicTrackingTokenFilter(TrackingTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Matcher matcher = PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            // フィルタの shouldNotFilter で除外しているはずだが、防衛的に通す
            chain.doFilter(request, response);
            return;
        }
        String expectedTn = matcher.group(1);
        String token = request.getParameter("token");
        if (token == null || token.isBlank()) {
            writeForbidden(response, "token クエリパラメータが必要です");
            return;
        }
        try {
            tokenService.verify(token, TrackingNumber.of(expectedTn));
        } catch (TrackingTokenInvalidException ex) {
            log.warn("公開追跡照会トークン検証失敗 (tn={}): {}", expectedTn, ex.getMessage());
            writeForbidden(response, "トークンが無効または期限切れです");
            return;
        } catch (IllegalArgumentException ex) {
            // TrackingNumber.of が拒否したケース（URL パターンで防いでいるが防衛）
            writeForbidden(response, "追跡番号フォーマットが不正です");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH_PATTERN.matcher(request.getRequestURI()).matches();
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"%s\"}",
                message.replace("\"", "\\\"")));
    }
}
