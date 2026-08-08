package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.cargotracker.booking.application.internal.commandservices
        .ApplyHandlingResultCommandService;
import com.example.cargotracker.booking.interfaces.events.BookingHandlingEventHandler;
import com.example.cargotracker.shared.domain.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 予約への反映を取りこぼしたとき、それが<strong>数えられる場所に出ている</strong>ことを
 * 確かめる（IT6 追補 A1 / ふりかえり C10）。
 *
 * <p>IT6 のふりかえり P1 は「入口は回したが、出口を回していない」ことだった。
 * <strong>判定そのもの（{@code NOT_FOUND} / {@code CONFLICTED} を返すか）は回してあるが、
 * その結果がどこへ行くかを一度も壊していなかった。</strong> ここで回すのは出口の側である。
 *
 * <p><strong>本テストは Booking 側に置く。</strong> 購読者ごとに 1 つのテストへ
 * まとめて {@code shared} に置くと、共有パッケージから 2 つの BC を参照することになり、
 * BC 分離のルール（ArchUnit ルール 4）に落ちる。<strong>検査の都合でルールを緩めない。</strong>
 */
@DisplayName("予約への反映の取りこぼし")
class BookingHandlingEventSkipTest {

    private static final UUID BOOKING_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventualConsistencySkips skips = new EventualConsistencySkips(registry);

    private static HandlingActivityRegisteredEvent event() {
        return new HandlingActivityRegisteredEvent(
                BOOKING_ID, "TRK-20260401-0042", "LOAD",
                Instant.parse("2026-04-01T00:00:00Z"), "JPOSA", "V0001", false);
    }

    @ParameterizedTest
    @EnumSource(value = ApplyHandlingResultCommandService.Result.class,
            names = {"NOT_FOUND", "CONFLICTED"})
    void 反映が失敗すると理由ごとに数えられる(
            ApplyHandlingResultCommandService.Result result) {
        var service = mock(ApplyHandlingResultCommandService.class);
        when(service.apply(any(), anyBoolean(), anyBoolean())).thenReturn(result);

        new BookingHandlingEventHandler(service, skips).on(event());

        assertThat(count(result.name())).isEqualTo(1.0);
    }

    /**
     * <strong>成功したときに数えてはならない。</strong> 常に増える数え方をすると
     * 閾値を決められず、「増えていること」に意味が無くなる。
     */
    @Test
    void 反映できたときは何も数えない() {
        var service = mock(ApplyHandlingResultCommandService.class);
        when(service.apply(any(), anyBoolean(), anyBoolean()))
                .thenReturn(ApplyHandlingResultCommandService.Result.APPLIED);

        new BookingHandlingEventHandler(service, skips).on(event());

        assertThat(registry.find(EventualConsistencySkips.METRIC_NAME).counters()).isEmpty();
    }

    private double count(String reason) {
        var counter = registry.find(EventualConsistencySkips.METRIC_NAME)
                .tag("subscriber", "booking")
                .tag("reason", reason)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
