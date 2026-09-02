package com.example.trackingms.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 公開の追跡照会の上限（[ADR-024] 決定 6）。
 *
 * <p>追跡番号は日付が既知なら 4 桁しかない。認証が無いので、総当たりを止める仕組みが
 * 他に無い。
 */
@DisplayName("公開照会の上限（ADR-024 決定 6）")
class PublicLookupThrottleFilterTest {

    private static final String PREFIX = "/api/v1/public/";
    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    /**
     * 進められる時計。
     *
     * <p><strong>新しいフィルタを作って確かめない。</strong>新しい実体の窓は空なので、
     * <strong>窓を数え直す実装を丸ごと消しても緑になる</strong>——時間で守る仕組みは、
     * 同じ実体のまま時間を進めて初めて判別できる（IT8 のクローズレビュー）。
     */
    private final java.util.concurrent.atomic.AtomicReference<Instant> now =
            new java.util.concurrent.atomic.AtomicReference<>(NOW);

    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private final PublicLookupThrottleFilter filter =
            new PublicLookupThrottleFilter(PREFIX, 3, clock);

    private int passedThrough;

    private final FilterChain chain = (request, response) -> passedThrough++;

    private MockHttpServletResponse call(String uri, String clientIp) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(clientIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    /** 上限を超えたら 429 で断る。 */
    @Test
    @DisplayName("上限を超えた照会は 429 で断る")
    void rejectsBeyondTheLimit() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(call(PREFIX + "tracking/TRK-1", "203.0.113.10").getStatus())
                    .as("%d 回目で断られた。上限に達していない", attempt)
                    .isEqualTo(200);
        }

        assertThat(call(PREFIX + "tracking/TRK-1", "203.0.113.10").getStatus()).isEqualTo(429);
    }

    /** 別の呼び出し元は、別に数える。1 人が使い切っても他の荷主は照会できる。 */
    @Test
    @DisplayName("呼び出し元ごとに数える")
    void countsPerClient() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            call(PREFIX + "tracking/TRK-1", "203.0.113.10");
        }

        assertThat(call(PREFIX + "tracking/TRK-1", "198.51.100.7").getStatus()).isEqualTo(200);
    }

    /**
     * <strong>ヘルスチェックと業務 API は対象外である。</strong>
     *
     * <p>横断的な防御を一律に掛けると、過負荷のときに liveness が 429 を返し、
     * Kubernetes が再起動ループに入る——防いだはずの過負荷を、自分で悪化させることになる。
     */
    @Test
    @DisplayName("公開の照会以外には掛からない")
    void exemptsEverythingOutsideThePublicPrefix() throws Exception {
        for (int attempt = 1; attempt <= 20; attempt++) {
            assertThat(call("/actuator/health", "203.0.113.10").getStatus())
                    .as("ヘルスチェックが %d 回目で断られた。再起動ループになる", attempt)
                    .isEqualTo(200);
            assertThat(call("/api/v1/tracking/manage/TRK-1", "203.0.113.10").getStatus())
                    .as("業務 API が %d 回目で断られた", attempt)
                    .isEqualTo(200);
        }

        assertThat(passedThrough).as("素通しされていない").isEqualTo(40);
    }

    /**
     * 窓が過ぎたら数え直す。1 分に 3 件でも、1 日中は使える。
     *
     * <p><strong>同じ実体のまま時間を進める。</strong>新しい実体で確かめると、
     * 数え直す実装を消しても緑になる。
     */
    @Test
    @DisplayName("窓が過ぎたら、数え直す")
    void resetsAfterTheWindow() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            call(PREFIX + "tracking/TRK-1", "203.0.113.10");
        }
        assertThat(call(PREFIX + "tracking/TRK-1", "203.0.113.10").getStatus())
                .as("まだ窓の中なのに数え直している")
                .isEqualTo(429);

        now.set(NOW.plus(PublicLookupThrottleFilter.WINDOW).plusSeconds(1));

        assertThat(call(PREFIX + "tracking/TRK-1", "203.0.113.10").getStatus())
                .as("窓が過ぎたのに数え直していない。1 分あたりでなく通算の上限になっている")
                .isEqualTo(200);
    }

    /**
     * <strong>覚えている呼び出し元が増え続けない。</strong>
     *
     * <p>認証の外にある経路で、呼び出し元ごとに 1 件ずつ増える。捨てる経路が無いと、
     * <strong>総当たりを防ぐ仕組みが、別の枯渇経路になる</strong>——送信元を詐称した
     * 要求を撒くだけでヒープを押し上げられる。
     */
    @Test
    @DisplayName("呼び出し元が増えすぎても、覚えている数に上限がある")
    void doesNotGrowWithoutBound() throws Exception {
        for (int i = 0; i < PublicLookupThrottleFilter.MAX_TRACKED_CLIENTS + 500; i++) {
            call(PREFIX + "tracking/TRK-1", "203.0.113." + i);
        }

        assertThat(trackedClients())
                .as("覚えている呼び出し元が増え続けている")
                .isLessThanOrEqualTo(PublicLookupThrottleFilter.MAX_TRACKED_CLIENTS);

        // **上限そのものは残る。**捨てたあとも、同じ相手からの連打は断る
        for (int attempt = 1; attempt <= 3; attempt++) {
            call(PREFIX + "tracking/TRK-1", "198.51.100.7");
        }
        assertThat(call(PREFIX + "tracking/TRK-1", "198.51.100.7").getStatus())
                .as("捨てたあとに上限が働かなくなっている")
                .isEqualTo(429);
    }

    /** 覚えている呼び出し元の数。実装の内側を見るのは、増え続けないことを見るためだけ。 */
    private int trackedClients() throws Exception {
        java.lang.reflect.Field field =
                PublicLookupThrottleFilter.class.getDeclaredField("windows");
        field.setAccessible(true);
        return ((java.util.Map<?, ?>) field.get(filter)).size();
    }
}
