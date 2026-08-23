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
    private final Clock clock;

    /** IP ごとの窓と件数。プロセス内に持つ——1 台で足りない規模になったら共有先へ移す。 */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

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
            response.getWriter().write("""
                    {"message":"照会が多すぎます。しばらくしてからお試しください"}""");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exceedsLimit(String clientIp) {
        Instant now = clock.instant();
        Window window = windows.compute(clientIp, (key, current) ->
                current == null || current.startedAt.plus(WINDOW).isBefore(now)
                        ? new Window(now)
                        : current);
        return window.count.incrementAndGet() > limitPerWindow;
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
