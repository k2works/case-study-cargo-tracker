package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.example.shared.domain.model.Location;

/**
 * 誤配の記録（US28-2・US28-8・[ADR-026] 決定 3）。
 *
 * <p><strong>状態と事実を分けて持つ。</strong>経路の状況は再設計で {@code ROUTED} へ
 * 戻るが、<strong>誤配が起きた事実は戻らない</strong>——料金調整の根拠として参照される。
 */
@DisplayName("誤配の記録")
class MisrouteTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Instant AT = Instant.parse("2026-09-05T00:00:00Z");

    private static Cargo inTransit() {
        return CargoRestoration.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                new CargoStatus(BookingStatus.IN_TRANSIT, TransportStatus.NOT_RECEIVED,
                        RoutingStatus.ROUTED),
                CargoSpecification.general(new BigDecimal("12000"), 20, "電子部品", null),
                RouteSpecification.restore(TOKYO, LOS_ANGELES,
                        LocalDate.of(2026, Month.SEPTEMBER, 1),
                        LocalDate.of(2026, Month.SEPTEMBER, 20)),
                CargoItinerary.of(List.of(
                        Leg.of(VoyageNumber.of("V0201"), TOKYO, LOS_ANGELES,
                                Instant.parse("2026-09-02T09:00:00Z"),
                                Instant.parse("2026-09-18T09:00:00Z")))),
                null, TrackingNumber.of("TRK-20260823-0001"));
    }

    @Test
    @DisplayName("予定ルート外の荷役で、経路の状況が誤配になる")
    void marksTheRoutingStatusAsMisrouted() {
        Cargo misrouted = inTransit().misrouted("SGSIN", AT);

        assertThat(misrouted.routingStatus()).isEqualTo(RoutingStatus.MISROUTED);
        assertThat(misrouted.isMisrouted()).isTrue();
    }

    /**
     * <strong>どこで外れたかまでが事実である</strong>（受入基準 28-3）。
     *
     * <p>「誤配があった」だけでは、荷主にも経理にも説明できない。
     */
    @Test
    @DisplayName("いつ・どこで外れたかを残す")
    void recordsWhenAndWhere() {
        Cargo misrouted = inTransit().misrouted("SGSIN", AT);

        assertThat(misrouted.misroute()).isPresent();
        assertThat(misrouted.misroute().orElseThrow().locationUnLocode()).isEqualTo("SGSIN");
        assertThat(misrouted.misroute().orElseThrow().at()).isEqualTo(AT);
    }

    /**
     * <strong>2 回目以降は最初の誤配を残す。</strong>
     *
     * <p>誤配のあと目的地へ向かわずに別の港でも荷役が起きることはある。
     * <strong>いつ経路から外れたか</strong>が料金調整の起点であり、最後に外れた場所ではない。
     */
    @Test
    @DisplayName("2 回目の誤配でも、最初に外れた場所と日時を残す")
    void keepsTheFirstMisroute() {
        Cargo twice = inTransit()
                .misrouted("SGSIN", AT)
                .misrouted("HKHKG", Instant.parse("2026-09-08T00:00:00Z"));

        assertThat(twice.misroute().orElseThrow().locationUnLocode())
                .as("最後に外れた場所で上書きされている。料金調整の起点が動く")
                .isEqualTo("SGSIN");
        assertThat(twice.misroute().orElseThrow().at()).isEqualTo(AT);
    }

    /**
     * <strong>キャンセル済みの予約は動かさない。</strong>
     *
     * <p>遅れて届いた荷役でキャンセルが覆ると、荷主との約束と記録が食い違う。
     */
    @Test
    @DisplayName("キャンセル済みの予約は誤配にしない")
    void doesNotTouchCancelledCargo() {
        Cargo cancelled = inTransit().cancel();

        Cargo unchanged = cancelled.misrouted("SGSIN", AT);

        assertThat(unchanged.bookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(unchanged.isMisrouted())
                .as("キャンセル済みの予約が誤配になっている。約束と記録が食い違う")
                .isFalse();
    }

    @Nested
    @DisplayName("誤配の事実そのもの")
    class TheRecordItself {

        @Test
        @DisplayName("日時の無い誤配は作れない")
        void requiresATime() {
            assertThatThrownBy(() -> new Misroute(null, "SGSIN"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 「誤配があった」だけでは、荷主にも経理にも説明できない。 */
        @Test
        @DisplayName("港の無い誤配は作れない")
        void requiresAPort() {
            assertThatThrownBy(() -> new Misroute(AT, " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
