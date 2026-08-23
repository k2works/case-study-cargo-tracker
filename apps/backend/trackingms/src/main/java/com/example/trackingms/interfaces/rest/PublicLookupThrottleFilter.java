package com.example.trackingms.interfaces.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 公開の追跡照会に上限を置く（[ADR-024] 決定 6）。
 *
 * <p>追跡番号は {@code TRK-yyyyMMdd-nnnn} で、<strong>日付が既知なら 4 桁しかない</strong>。
 * 認証が無いので、総当たりを止める仕組みが他に無い。
 *
 * <p><strong>ヘルスチェックは対象外である。</strong>横断的な防御を一律に掛けると、
 * 過負荷のときに liveness が 429 を返し、Kubernetes が再起動ループに入る——防いだはずの
 * 過負荷を、自分で悪化させることになる。ここは公開の照会だけに掛ける。
 *
 * <p>上限は荷主 1 人の使い方（1 日数回）から十分に離してある。運用で変えられるよう、
 * 値は設定から受け取る。
 */
public class PublicLookupThrottleFilter extends HttpFilter {

    /** 数える窓。短くすると、まとめて叩く相手を取り逃がす。 */
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final String pathPrefix;
    private final int limitPerWindow;
    // HttpFilter は Serializable を継承するが、この実装を直列化する経路は無い
    private final transient Clock clock;

    /**
     * IP ごとの窓と件数。
     *
     * <p><strong>放っておくと増え続ける。</strong>認証の外にある経路で、呼び出し元ごとに
     * 1 件ずつ増える——<strong>総当たりを防ぐ仕組みが、別の枯渇経路になる</strong>。
     * 窓が過ぎたものを捨て、上限も置く。
     *
     * <p>プロセス内に持つ。<strong>台数を増やすと実効の上限も台数倍になる</strong>
     * ——1 台で足りない規模になったら共有先へ移す（[ADR-024] 決定 6 の備考）。
     */
    private final transient Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 覚えておく呼び出し元の上限。
     *
     * <p>超えたら、窓の過ぎたものを捨てる。それでも減らなければ全部捨てる
     * ——<strong>数え直しになるが、落ちるよりよい</strong>。上限そのものは残る。
     */
    static final int MAX_TRACKED_CLIENTS = 10_000;

    public PublicLookupThrottleFilter(String pathPrefix, int limitPerWindow, Clock clock) {
        this.pathPrefix = pathPrefix;
        this.limitPerWindow = limitPerWindow;
        this.clock = clock;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!request.getRequestURI().startsWith(pathPrefix)) {
            // **ヘルスチェックも業務 API も対象外。**公開の照会だけに掛ける
            chain.doFilter(request, response);
            return;
        }

        String clientIp = PublicTrackingController.clientIpOf(request);
        if (exceedsLimit(clientIp)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter()
                    .write("{\"message\":\"照会が多すぎます。しばらくしてからお試しください\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exceedsLimit(String clientIp) {
        Instant now = clock.instant();
        evictIfCrowded(now);
        Window window = windows.compute(clientIp, (key, current) ->
                current == null || expired(current, now) ? new Window(now) : current);
        return window.count.incrementAndGet() > limitPerWindow;
    }

    private boolean expired(Window window, Instant now) {
        return window.startedAt.plus(WINDOW).isBefore(now);
    }

    /**
     * 覚えている呼び出し元が増えすぎたら捨てる。
     *
     * <p>まず窓の過ぎたものを捨てる。それでも上限を超えているなら全部捨てる——
     * <strong>数え直しになるが、際限なく持つよりよい</strong>。
     */
    private void evictIfCrowded(Instant now) {
        if (windows.size() < MAX_TRACKED_CLIENTS) {
            return;
        }
        windows.values().removeIf(window -> expired(window, now));
        if (windows.size() >= MAX_TRACKED_CLIENTS) {
            windows.clear();
        }
    }

    /** 1 つの IP の窓。 */
    private static final class Window {
        private final Instant startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
