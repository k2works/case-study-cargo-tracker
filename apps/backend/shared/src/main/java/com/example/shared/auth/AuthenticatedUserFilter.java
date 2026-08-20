package com.example.shared.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gateway が付けた利用者ヘッダを必須とするフィルタ（ADR-007）。
 *
 * <p>[ADR-004] は「JWT の署名検証は Gateway に一元化し、各サービスはヘッダを信じて認可だけを行う」
 * と決めた。その決定は「Gateway を通らずにサービスへ到達できない」ことを前提にしている。
 * ヘッダの有無を各エンドポイントの引数に任せると、書き忘れた 1 本だけが無認証で開く。
 * ロールの集合が空になるだけで、認可を書いていないエンドポイントは素通りするためである。
 * だから横断的に、認可判定より前に弾く。
 *
 * <p><strong>ヘルスチェックは必ず除外する。</strong> 横断的な防御を一律に適用すると、
 * Kubernetes の liveness / readiness が 401 を受けて再起動ループに入る（IT1 で Gateway の
 * JWT フィルタが同じ形で失敗した）。認証を必要としない公開エンドポイント（追跡照会・ログイン）は
 * サービスごとに異なるため、登録時に渡す。
 */
public class AuthenticatedUserFilter extends HttpFilter {

    /** 解決済みの利用者を後続へ渡す属性名。 */
    public static final String ATTRIBUTE = AuthenticatedUser.class.getName();

    /**
     * 常に除外するパス。
     *
     * <p>ヘルスチェックの除外はサービスの裁量にしない。裁量にすると、除外を書き忘れた
     * サービスだけが再起動ループに入る。
     */
    private static final List<String> ALWAYS_OPEN = List.of("/actuator/health");

    private final List<String> openPathPrefixes;

    /** 公開エンドポイントを持たないサービス用。 */
    public AuthenticatedUserFilter() {
        this(List.of());
    }

    /**
     * @param openPathPrefixes 認証不要で公開するパスの接頭辞（例: ログイン・公開追跡照会）。
     *     ヘルスチェックは指定不要で常に開いている
     */
    public AuthenticatedUserFilter(List<String> openPathPrefixes) {
        this.openPathPrefixes = List.copyOf(openPathPrefixes);
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI();
        if (isOpen(path)) {
            chain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader(AuthenticatedUser.USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            reject(response);
            return;
        }

        request.setAttribute(ATTRIBUTE,
                AuthenticatedUser.of(userId, request.getHeader(AuthenticatedUser.ROLES_HEADER)));
        chain.doFilter(request, response);
    }

    private boolean isOpen(String path) {
        return ALWAYS_OPEN.stream().anyMatch(path::startsWith)
                || openPathPrefixes.stream().anyMatch(path::startsWith);
    }

    /**
     * 拒否の理由は返さない。
     *
     * <p>「ヘッダが無い」と教えることは、Gateway を迂回できたことを攻撃者に確認させる。
     * 利用者から見れば認証が切れた状態と区別がつかないため、同じ文言で返す。
     */
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"認証が必要です\"}");
    }
}
