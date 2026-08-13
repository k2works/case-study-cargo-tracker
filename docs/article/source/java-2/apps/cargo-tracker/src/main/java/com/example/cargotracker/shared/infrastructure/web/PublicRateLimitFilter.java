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
 * <h2>プロキシの背後</h2>
 *
 * <p>ALB の背後では接続元が全員同じになる。そのまま数えると
 * <strong>誰か 1 人の総当たりで全員が締め出される</strong>（制限が業務妨害に変わる）。
 * {@code cargotracker.public-rate-limit.trusted-proxy-count} に信頼できる段数を設定すると、
 * その段数だけ右から遡った値を実クライアントとして数える。
 * <strong>既定の 0 はヘッダを一切信用しない。</strong>
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

        if (allow(clientKey(request, properties.trustedProxyCount()))) {
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
     * 数える単位（ADR-011）。
     *
     * <p><strong>信頼できるプロキシの段数だけ、右から遡った値を実クライアントとする。</strong>
     *
     * <p>{@code X-Forwarded-For} は「クライアントが名乗った値 + 各プロキシが追記した値」の
     * 並びである。<strong>左端は送信元が自由に名乗れる</strong>ため、左から採ると
     * ヘッダを変えるだけで制限を回避できる。右端は直前のプロキシが書いた値であり、
     * 自分が信頼した段数のぶんだけ遡った位置が、<strong>偽装できない最も左の値</strong>になる。
     *
     * <p><strong>段数 0（既定）ではヘッダを一切見ない。</strong> 直結の構成で
     * ヘッダを信用すると、防御が事実上無くなる。設定を忘れた環境が安全側に倒れる。
     *
     * <p>ヘッダが足りない・空のときは接続元にたおす。<strong>プロキシ設定の誤りで
     * 制限が外れる</strong>ほうが、厳しく数えるより危険である。
     */
    private static String clientKey(HttpServletRequest request, int trustedProxyCount) {
        String remote = request.getRemoteAddr();
        String fallback = remote == null ? "unknown" : remote;
        if (trustedProxyCount <= 0) {
            return fallback;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return fallback;
        }

        String[] hops = forwarded.split(",");
        int index = hops.length - trustedProxyCount;
        if (index < 0) {
            // 名乗りが段数に足りない。**足りない側を信用しない**
            return fallback;
        }
        String candidate = hops[index].trim();
        return candidate.isEmpty() ? fallback : candidate;
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
