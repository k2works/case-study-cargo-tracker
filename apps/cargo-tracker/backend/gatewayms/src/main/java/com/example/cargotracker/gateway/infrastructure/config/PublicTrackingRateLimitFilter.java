package com.example.cargotracker.gateway.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 公開照会の総当たり対策（ui_design.md「総当たり対策」/ US18）。
 *
 * <p><b>認証不要経路の唯一の防御である。</b> 追跡番号を推測しにくい形式にしても
 * （[ADR-0011]）、回数を絞らなければ総当たりは通る。形式と回数は対で成り立つ。</p>
 *
 * <p><b>数えるのは公開照会だけ。</b> ヘルスチェックまで数えると、過負荷のときに
 * liveness が 429 を返して再起動ループに入る（IT3・IT7 で同型の欠陥）。認証の要る
 * 経路も数えない——そちらは誰の操作か分かるので、絞るなら利用者単位である。</p>
 *
 * <p><b>締め出しではない。</b> 1 分の窓が過ぎればまた通る。荷受人は同じ番号を
 * 何度も見に来る（画面が 30 秒ごとに更新する）ので、恒久的に断ると業務が止まる。</p>
 *
 * <p><b>限界を書いておく。</b> 数えるのは接続元アドレスなので、同じ社内 NAT の
 * 内側からは互いに巻き添えになる。逆に、多数のアドレスを持つ相手は素通りする。
 * <b>これは総当たりを不可能にする仕掛けではなく、割に合わなくする仕掛けである</b>
 * （36^10 通りを 1 分 10 回で舐めるには現実的でない時間がかかる）。</p>
 */
public class PublicTrackingRateLimitFilter extends OncePerRequestFilter {

    /** 数える対象。{@code /actuator/**} と認証の要る経路は入らない。 */
    private static final String PUBLIC_TRACKING_PREFIX = "/api/v1/tracking/public/";

    /** 1 分に 10 回まで（ui_design.md）。 */
    static final int LIMIT = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * 覚えておくアドレスの上限。
     *
     * <p><b>無制限に持たない。</b> アドレスを変えながら叩かれると、断る側の
     * メモリが先に尽きる（防御が攻撃の的になる）。上限に達したら丸ごと捨てる
     * ——古い順に消すより単純で、捨てても失うのは「数えかけ」だけである。</p>
     */
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicTrackingRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    /** ある接続元の、ある 1 分間の回数。 */
    private record Window(Instant startedAt, int count) {
        Window next(Instant now) {
            return startedAt.plus(WINDOW).isAfter(now)
                    ? new Window(startedAt, count + 1)
                    : new Window(now, 1);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(PUBLIC_TRACKING_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String address = clientAddress(request);
        Instant now = clock.instant();
        Window window = windows.get(address);
        if (window != null && window.startedAt().plus(WINDOW).isAfter(now)
                && window.count() >= LIMIT) {
            // **待てば通ることを伝える。** 断られた側は、番号が違うのか回数なのかを
            // 応答から読めないと、正しい番号を疑い続ける（画面が文言で書き分ける）。
            response.setStatus(429);
            response.setHeader("Retry-After",
                    String.valueOf(remainingSeconds(window.startedAt(), now)));
            return;
        }

        chain.doFilter(request, response);

        if (response.getStatus() == 404) {
            countMiss(address, now);
        }
    }

    /** 外れを 1 つ数える。当たりは数えない（総当たりは外れでしか進まない）。 */
    private void countMiss(String address, Instant now) {
        if (windows.size() > MAX_TRACKED_ADDRESSES) {
            windows.clear();
        }
        windows.compute(address,
                (key, current) -> current == null ? new Window(now, 1) : current.next(now));
    }

    /**
     * 接続元。<b>中継機の内側では転送元を見る</b>。
     *
     * <p>Ingress やリバースプロキシ越しだと {@code getRemoteAddr()} は中継機の
     * アドレスになり、<b>全利用者が 1 つのカウンタを共有</b>して常時断られる。
     * {@code X-Forwarded-For} の先頭が本来の接続元である。</p>
     *
     * <p><b>これは詐称できる。</b> 詐称されればカウンタを分散できるが、<b>詐称
     * しない相手を巻き添えにするほうが実害が大きい</b>。総当たりの本命の防ぎは
     * 番号の広さ（36^10）で、回数はそれを補う。</p>
     */
    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        int comma = forwarded.indexOf(',');
        return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }

    private static long remainingSeconds(Instant windowStart, Instant now) {
        long remaining = Duration.between(now, windowStart.plus(WINDOW)).toSeconds();
        return Math.max(remaining, 1);
    }
}
