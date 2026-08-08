package com.example.cargotracker.shared.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 公開エンドポイントのレートリミット（ADR-011 / {@code non_functional.md} §2.2）。
 *
 * <p>公開追跡は<strong>認証を持たない相手に開いた唯一の入口</strong>であり、
 * 追跡番号は日付＋連番という<strong>推測できる形</strong>をしている。
 * 総当たりを止める手立てが無いと、貨物の有無を端から確かめられる。
 *
 * <h2>限界</h2>
 *
 * <p><strong>数えるのはこのプロセスの中だけである。</strong> N 台構成では
 * 実効的な上限が N 倍になる。分散カウンタ（Redis 等）は基盤の判断を要するため
 * 本 IT では入れない（ADR-011）。
 *
 * <p><strong>これは「入れないより安全」という判断であって、十分という意味ではない。</strong>
 * 記録しておかないと、次に読む人は上限が守られていると信じる。
 *
 * <h2>除外</h2>
 *
 * <p><strong>{@code /actuator/health} は対象にしない</strong>（{@code non_functional.md} §3.4）。
 * 過負荷時にヘルスチェックまで制限されると、liveness プローブが 503 を返して
 * ECS がタスクを再起動し、<strong>残ったタスクの負荷がさらに上がって次も 503 になる</strong>。
 * <strong>負荷を減らすための防御が、負荷を増やす再起動ループを引き起こす。</strong>
 *
 * <p>業務画面も対象にしない。認証で守られており、ここまで制限すると
 * 繁忙期に現場が締め出される。
 */
@Component
@ConditionalOnProperty(
        prefix = "cargotracker.public-rate-limit", name = "enabled", havingValue = "true")
public class PublicRateLimitFilter extends OncePerRequestFilter {

    /** 制限の対象。**公開エンドポイントだけである。** */
    private static final String PUBLIC_PREFIX = "/public/";

    private final PublicRateLimitProperties properties;
    private final Clock clock;

    /**
     * 送信元ごとの窓。
     *
     * <p><strong>際限なく増やさない。</strong> 窓を跨いだ入口で古い記録を捨てる。
     * 捨てないと、送信元の数だけ記録が残り続けてメモリを食い潰す
     * （防御が別の障害を作る形は、ヘルスチェックの除外と同じ話である）。
     */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicRateLimitFilter(PublicRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 公開エンドポイント以外は素通しする。
     *
     * <p><strong>ここが除外の実体である。</strong> {@code /actuator/health} も
     * 業務画面もこの条件で外れる。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (allow(clientKey(request))) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, properties.window().toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        // **機械可読な形でも返す。** 画面の文言だけでは、ブラウザ以外
        //（監視・スクリプト）が待つべき時間を知れない
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("text/html;charset=UTF-8");
        // **文字列連結で組み立てる。** 書式文字列にすると SpotBugs が
        // 改行に %n を使うよう促すが、HTTP のレスポンス本文に
        // プラットフォーム依存の改行を入れる理由は無い
        response.getWriter().write(
                "<!DOCTYPE html>\n"
                + "<html lang=\"ja\"><head><meta charset=\"utf-8\">\n"
                + "<title>しばらくお待ちください | Cargo Tracker</title></head>\n"
                + "<body><main>\n"
                + "<h1>アクセスが集中しています</h1>\n"
                + "<p>しばらくしてからもう一度お試しください（目安: "
                + retryAfterSeconds + " 秒後）。</p>\n"
                + "</main></body></html>\n");
    }

    /**
     * 窓の中で許すか。
     *
     * <p><strong>送信元ごとに数える。</strong> 全体で 1 つの数え方にすると、
     * 誰か 1 人の総当たりで全員が締め出される。
     */
    private boolean allow(String key) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (k, current) ->
                current == null || current.isExpired(now, properties.window())
                        ? new Window(now)
                        : current);
        return window.count().incrementAndGet() <= properties.requestsPerWindow();
    }

    /**
     * 数える単位。
     *
     * <p><strong>プロキシのヘッダを信じない。</strong> {@code X-Forwarded-For} は
     * 送信元が自由に名乗れるため、それで数えると<strong>ヘッダを変えるだけで
     * 制限を回避できる</strong>。ALB の背後で実 IP を得るには、
     * 信頼できるプロキシを明示した上での {@code ForwardedHeaderFilter} が要る
     * （基盤の設定であり ADR-011 の残課題）。
     */
    private static String clientKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    /** 窓 1 つ分。開始時刻と、その窓で数えた回数。 */
    private record Window(Instant startedAt, AtomicInteger count) {

        Window(Instant startedAt) {
            this(startedAt, new AtomicInteger());
        }

        boolean isExpired(Instant now, java.time.Duration length) {
            return !now.isBefore(startedAt.plus(length));
        }
    }
}
