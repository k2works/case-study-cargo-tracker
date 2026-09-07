package com.example.cargotracker.gateway.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 公開照会の総当たり対策（ui_design.md「総当たり対策」/ US18）。
 *
 * <p><b>認証不要経路の唯一の防御である。</b> 番号を推測しにくい形式にしても
 * （[ADR-0011]）、回数を絞らなければ総当たりは通る。形式と回数は対で成り立つ。</p>
 */
class PublicTrackingRateLimitFilterTest {

    private static final Instant START = Instant.parse("2026-09-11T02:00:00Z");

    /** 進められる時計。窓が開くことを実時間を待たずに確かめる。 */
    private static final class MovableClock extends Clock {
        private Instant now = START;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    private final MovableClock clock = new MovableClock();
    private final AtomicInteger passed = new AtomicInteger();
    private final FilterChain chain = (request, response) -> passed.incrementAndGet();

    private final PublicTrackingRateLimitFilter filter = new PublicTrackingRateLimitFilter(clock);

    private MockHttpServletResponse call(String path, String ip) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("同一 IP から 1 分に 10 回を超える公開照会は 429")
    void rejectsTheEleventhRequestWithinAMinute() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("/api/v1/tracking/public/TRK-8K2QX7M4RB", "10.0.0.1").getStatus())
                    .as("%d 回目", i + 1)
                    .isEqualTo(200);
        }

        var rejected = call("/api/v1/tracking/public/TRK-8K2QX7M4RC", "10.0.0.1");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(passed.get()).as("断った分は後段へ流さない").isEqualTo(10);
    }

    @Test
    @DisplayName("1 分たてば また通る（締め出しではない）")
    void allowsAgainAfterTheWindow() throws Exception {
        for (int i = 0; i < 11; i++) {
            call("/api/v1/tracking/public/TRK-8K2QX7M4RB", "10.0.0.2");
        }

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        assertThat(call("/api/v1/tracking/public/TRK-8K2QX7M4RB", "10.0.0.2").getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("別の IP は巻き添えにしない")
    void countsPerAddress() throws Exception {
        for (int i = 0; i < 11; i++) {
            call("/api/v1/tracking/public/TRK-8K2QX7M4RB", "10.0.0.3");
        }

        assertThat(call("/api/v1/tracking/public/TRK-8K2QX7M4RB", "10.0.0.4").getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("ヘルスチェックは数えない（過負荷で liveness が落ちると再起動ループ）")
    void doesNotCountHealthProbes() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(call("/actuator/health", "10.0.0.5").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("認証のある経路は数えない（ここで守るのは認証不要の照会だけ）")
    void doesNotCountAuthenticatedPaths() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(call("/api/v1/booking/bookings", "10.0.0.6").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("断るときは待てば通ることを伝える（Retry-After）")
    void tellsHowLongToWait() throws Exception {
        for (int i = 0; i < 11; i++) {
            call("/api/v1/tracking/public/TRK-8K2QX7M4RB", "10.0.0.7");
        }

        var rejected = call("/api/v1/tracking/public/TRK-8K2QX7M4RB", "10.0.0.7");

        assertThat(rejected.getHeader("Retry-After")).isNotNull();
    }
}
