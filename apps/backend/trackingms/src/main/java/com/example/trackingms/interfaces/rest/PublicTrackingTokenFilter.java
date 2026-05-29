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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公開追跡照会エンドポイント {@code /api/v1/public/tracking/{tn}} 向けの JWT 検証フィルタ（US18 / ADR-0013）。
 *
 * <p>本フィルタは Spring Security に依存せず、Spring Boot 標準の {@link OncePerRequestFilter} として
 * 直接 servlet チェーンに登録される（trackingms には Spring Security が導入されていないため）。
 * IT8 で trackingms 全体に Spring Security を導入する際は、本フィルタを SecurityFilterChain の中で
 * {@code permitAll} の前に登録するよう移行する。</p>
 *
 * <p>検証ロジックは {@link TrackingTokenService#verify(String, TrackingNumber)} に委譲し、
 * 失敗時はすべて <strong>HTTP 403 Forbidden + Problem Detail JSON</strong> を返す
 * （ui_design.md L738 準拠）。リソース存在の秘匿のため、401 ではなく 403 を採用する。</p>
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

    /**
     * {@link PublicTrackingTokenFilter} を servlet チェーンに登録する設定。
     * Spring Security 経由ではなく、Boot の {@link FilterRegistrationBean} で直接登録する。
     */
    @Configuration
    public static class FilterRegistration {
        @Bean
        public FilterRegistrationBean<PublicTrackingTokenFilter> publicTrackingTokenFilterRegistration(
                TrackingTokenService tokenService) {
            FilterRegistrationBean<PublicTrackingTokenFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new PublicTrackingTokenFilter(tokenService));
            bean.addUrlPatterns("/api/v1/public/tracking/*");
            bean.setOrder(1);
            return bean;
        }
    }
}
