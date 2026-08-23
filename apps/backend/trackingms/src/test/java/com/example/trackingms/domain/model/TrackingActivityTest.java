package com.example.trackingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 貨物の追跡（US14-3）。IT6 で作るのは追跡の開始までである。 */
@DisplayName("貨物の追跡")
class TrackingActivityTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final LocalDate DEADLINE = LocalDate.of(2030, Month.SEPTEMBER, 20);

    private static TrackingActivity started() {
        return TrackingActivity.start(TrackingNumber.of("TRK-20260822-0001"),
                TrackingBookingId.of("BKG-2026000001"), TOKYO, LOS_ANGELES, DEADLINE);
    }

    @Test
    @DisplayName("始めると受領待ちになり、まだ動いていない状態を持つ")
    void startsAsNotReceived() {
        TrackingActivity activity = started();

        // 「まだ受け取っていない」は空欄ではなく意味のある状態（ADR-009）
        assertThat(activity.trackingStatus()).isEqualTo(TrackingStatus.NOT_RECEIVED);
        assertThat(activity.trackingNumber().value()).isEqualTo("TRK-20260822-0001");
        assertThat(activity.bookingId().value()).isEqualTo("BKG-2026000001");
        assertThat(activity.origin()).isEqualTo(TOKYO);
        assertThat(activity.destination()).isEqualTo(LOS_ANGELES);
        assertThat(activity.arrivalDeadline()).isEqualTo(DEADLINE);
        // 採番も id の付与も永続化の側が行う（ADR-022 決定 7）
        assertThat(activity.id()).isNull();
    }

    @Test
    @DisplayName("欠けている項目があれば始められない")
    void rejectsMissingValues() {
        TrackingNumber number = TrackingNumber.of("TRK-20260822-0001");
        TrackingBookingId booking = TrackingBookingId.of("BKG-2026000001");

        assertThatThrownBy(() -> TrackingActivity.start(null, booking, TOKYO, LOS_ANGELES,
                DEADLINE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingActivity.start(number, null, TOKYO, LOS_ANGELES,
                DEADLINE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingActivity.start(number, booking, null, LOS_ANGELES,
                DEADLINE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingActivity.start(number, booking, TOKYO, null,
                DEADLINE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingActivity.start(number, booking, TOKYO, LOS_ANGELES,
                null)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * <strong>復元では検査しない</strong>（[ADR-012]）。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。
     */
    @Test
    @DisplayName("復元では検査しない")
    void restoreDoesNotValidate() {
        TrackingActivity restored = TrackingActivity.restore(1L, null, null,
                TrackingStatus.NOT_RECEIVED, TOKYO, LOS_ANGELES, DEADLINE);

        assertThat(restored.id()).isEqualTo(1L);
        assertThat(restored.trackingNumber()).isNull();
    }

    @Nested
    @DisplayName("値オブジェクト")
    class ValueObjects {

        /**
         * <strong>採番しない。</strong>
         *
         * <p>採番するのは bookingms である（[ADR-022] 決定 7）。ここが受け取るのは採番済みの
         * 番号であり、形式は向こうが決める。だから<strong>形式の検査はしない</strong>——
         * ここで形式を決めると、番号の形を変えるときに 2 か所を直すことになる。
         */
        @Test
        @DisplayName("追跡番号は空でなければ受け入れる（形式は採番する側が決める）")
        void acceptsAnyNonBlankTrackingNumber() {
            assertThat(TrackingNumber.of("TRK-20260822-0001").value())
                    .isEqualTo("TRK-20260822-0001");
            assertThat(TrackingNumber.of("TRK-20260822-0001")).hasToString("TRK-20260822-0001");

            assertThatThrownBy(() -> TrackingNumber.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrackingNumber.of("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("追跡番号の復元では検査しない")
        void restoresTrackingNumberWithoutValidation() {
            assertThat(TrackingNumber.restore("")).isEqualTo(new TrackingNumber(""));
            assertThat(TrackingNumber.restore(null)).isNull();
        }

        @Test
        @DisplayName("予約番号は空でなければ受け入れる")
        void acceptsAnyNonBlankBookingId() {
            assertThat(TrackingBookingId.of("BKG-2026000001").value())
                    .isEqualTo("BKG-2026000001");
            assertThat(TrackingBookingId.of("BKG-2026000001")).hasToString("BKG-2026000001");

            assertThatThrownBy(() -> TrackingBookingId.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TrackingBookingId.of(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("予約番号の復元では検査しない")
        void restoresBookingIdWithoutValidation() {
            assertThat(TrackingBookingId.restore("")).isEqualTo(new TrackingBookingId(""));
            assertThat(TrackingBookingId.restore(null)).isNull();
        }

        /**
         * IT6 で使う輸送の状況は 1 つだけ。
         *
         * <p><strong>値集合は設計（domain-model.md の TrackingStatus）が正である。</strong>
         * 実装だけが独自に増えると、設計を読んだ人が知らない状態が本番に現れる。
         */
        @Test
        @DisplayName("追跡の状況は設計の 9 値")
        void matchesTheDesignedValues() {
            assertThat(TrackingStatus.values()).containsExactly(
                    TrackingStatus.NOT_RECEIVED, TrackingStatus.RECEIVED, TrackingStatus.LOADED,
                    TrackingStatus.ONBOARD_CARRIER, TrackingStatus.UNLOADED,
                    TrackingStatus.AWAITING_CLAIM, TrackingStatus.CLAIMED,
                    TrackingStatus.EXCEPTION, TrackingStatus.UNKNOWN);
        }

        /**
         * 荷役の種別から進む先を導く（US15-4・[ADR-023] 決定 5）。
         *
         * <p><strong>目的港での荷降しだけは行き先が違う。</strong>途中の港なら次の積込を待ち、
         * 目的港なら荷受人の引取を待つ。同じ「荷降し」でも、貨物にとっての意味が違う。
         */
        @Test
        @DisplayName("荷役の種別から進む先が決まる")
        void derivesTheNextStatusFromHandling() {
            assertThat(TrackingStatus.afterHandling("RECEIVE", false))
                    .contains(TrackingStatus.RECEIVED);
            assertThat(TrackingStatus.afterHandling("LOAD", false))
                    .contains(TrackingStatus.LOADED);
            assertThat(TrackingStatus.afterHandling("UNLOAD", false))
                    .as("途中の港での荷降しは、次の積込を待つ")
                    .contains(TrackingStatus.UNLOADED);
            assertThat(TrackingStatus.afterHandling("UNLOAD", true))
                    .as("目的港での荷降しは、荷受人の引取を待つ")
                    .contains(TrackingStatus.AWAITING_CLAIM);
            assertThat(TrackingStatus.afterHandling("CLAIM", true))
                    .contains(TrackingStatus.CLAIMED);
        }

        /** 導けない種別で止めない。例外にすると、購読側が種別 1 つで止まる。 */
        @Test
        @DisplayName("知らない種別では進む先を決めない")
        void doesNotGuessForUnknownHandling() {
            assertThat(TrackingStatus.afterHandling("CUSTOMS_INSPECTION", false)).isEmpty();
        }
    }
}
