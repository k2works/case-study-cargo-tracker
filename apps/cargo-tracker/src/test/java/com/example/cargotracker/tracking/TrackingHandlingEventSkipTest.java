package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import com.example.cargotracker.tracking.application.internal.commandservices
        .RecordTrackingEventCommandService;
import com.example.cargotracker.tracking.interfaces.events.TrackingHandlingEventHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 追跡への反映を取りこぼしたとき、それが<strong>数えられる場所に出ている</strong>ことを
 * 確かめる（IT6 追補 A1 / ふりかえり C10）。
 *
 * <p>結果整合にした以上、購読側の失敗は利用者の画面に返せない。
 * <strong>返せないこと自体は代償であり避けられないが、気づく手段が無いことは別の問題である。</strong>
 */
@DisplayName("追跡への反映の取りこぼし")
class TrackingHandlingEventSkipTest {

    private static final String TRACKING_NUMBER = "TRK-20260401-0042";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventualConsistencySkips skips = new EventualConsistencySkips(registry);

    private static HandlingActivityRegisteredEvent event() {
        return new HandlingActivityRegisteredEvent(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                TRACKING_NUMBER, "LOAD",
                Instant.parse("2026-04-01T00:00:00Z"), "JPOSA", "V0001", false);
    }

    @ParameterizedTest
    @EnumSource(value = RecordTrackingEventCommandService.Result.class,
            names = {"NOT_FOUND", "CONFLICTED"})
    void 反映が失敗すると理由ごとに数えられる(
            RecordTrackingEventCommandService.Result result) {
        var service = mock(RecordTrackingEventCommandService.class);
        when(service.recordEvent(anyString(), any(), any(), anyString(), any()))
                .thenReturn(result);

        new TrackingHandlingEventHandler(service, skips).on(event());

        assertThat(count(result.name())).isEqualTo(1.0);
    }

    /** 成功したときに数えない（常に増える数え方では閾値を決められない）。 */
    @Test
    void 反映できたときは何も数えない() {
        var service = mock(RecordTrackingEventCommandService.class);
        when(service.recordEvent(anyString(), any(), any(), anyString(), any()))
                .thenReturn(RecordTrackingEventCommandService.Result.RECORDED);

        new TrackingHandlingEventHandler(service, skips).on(event());

        assertThat(registry.find(EventualConsistencySkips.METRIC_NAME).counters()).isEmpty();
    }

    private double count(String reason) {
        var counter = registry.find(EventualConsistencySkips.METRIC_NAME)
                .tag("subscriber", "tracking")
                .tag("reason", reason)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
