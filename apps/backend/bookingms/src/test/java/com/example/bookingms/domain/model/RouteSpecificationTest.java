package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.shared.domain.model.Location;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("輸送条件")
class RouteSpecificationTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    private static final ZoneId TOKYO_ZONE = ZoneId.of("Asia/Tokyo");

    /** 時間を進められる Clock。固定 Clock だと日付境界をまたぐ振る舞いを通れない。 */
    private final AtomicReference<Instant> currentTime =
            new AtomicReference<>(Instant.parse("2026-08-20T01:00:00Z"));

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return TOKYO_ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(currentTime.get(), zone);
        }

        @Override
        public Instant instant() {
            return currentTime.get();
        }
    };

    private RouteSpecification specification(LocalDate arrivalDeadline, ZoneId destinationZone) {
        return RouteSpecification.of(
                TOKYO, LOS_ANGELES, null, arrivalDeadline, destinationZone, clock);
    }

    @Test
    @DisplayName("出発地・目的地・到着期限を保持する")
    void holdsValues() {
        RouteSpecification spec = RouteSpecification.of(TOKYO, LOS_ANGELES,
                LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 20), LA, clock);

        assertThat(spec.origin()).isEqualTo(TOKYO);
        assertThat(spec.destination()).isEqualTo(LOS_ANGELES);
        assertThat(spec.departureDate()).contains(LocalDate.of(2026, Month.SEPTEMBER, 1));
        assertThat(spec.arrivalDeadline()).isEqualTo(LocalDate.of(2026, Month.SEPTEMBER, 20));
    }

    @Test
    @DisplayName("希望出発日は任意（荷主が指定しないことがある）")
    void allowsMissingDepartureDate() {
        assertThat(specification(LocalDate.of(2026, Month.SEPTEMBER, 20), LA).departureDate()).isEmpty();
    }

    @Test
    @DisplayName("出発地と目的地が同じ輸送は受け付けない")
    void rejectsSameOriginAndDestination() {
        LocalDate deadline = LocalDate.of(2026, Month.SEPTEMBER, 20);

        assertThatThrownBy(() ->
                RouteSpecification.of(TOKYO, TOKYO, null, deadline, TOKYO_ZONE, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同じにできません");
    }

    @Test
    @DisplayName("到着期限に過去の日付は指定できない")
    void rejectsPastDeadline() {
        LocalDate past = LocalDate.of(2020, Month.JANUARY, 1);

        assertThatThrownBy(() -> specification(past, LA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着期限");
    }

    @Test
    @DisplayName("期限が当日なら受け付ける（当日はまだ間に合う）")
    void acceptsDeadlineToday() {
        // 2026-08-20T01:00Z は LA では 2026-08-19。当日を刈ると、その日に着く便が組めない
        assertThatCode(() -> specification(LocalDate.of(2026, Month.AUGUST, 19), LA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("「今日」は目的地の暦で決める（UTC で判断しない）")
    void usesDestinationCalendar() {
        // 2026-08-20T01:00Z のとき、東京は 8/20、LA は 8/19。
        // UTC や出発地の暦で判断すると、LA では今日である 8/19 が「過去」として拒否される
        assertThatCode(() -> specification(LocalDate.of(2026, Month.AUGUST, 19), LA))
                .doesNotThrowAnyException();
        LocalDate today = LocalDate.of(2026, Month.AUGUST, 19);

        assertThatThrownBy(() -> specification(today, TOKYO_ZONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("日付が変わると、昨日までの期限は受け付けなくなる")
    void followsTheClockAcrossMidnight() {
        LocalDate deadline = LocalDate.of(2026, Month.AUGUST, 19);
        assertThatCode(() -> specification(deadline, LA)).doesNotThrowAnyException();

        // LA が翌日になるまで進める
        currentTime.set(Instant.parse("2026-08-21T01:00:00Z"));

        assertThatThrownBy(() -> specification(deadline, LA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("希望出発日が到着期限より後の要件は受け付けない")
    void rejectsDepartureAfterDeadline() {
        LocalDate departure = LocalDate.of(2026, Month.SEPTEMBER, 21);
        LocalDate deadline = LocalDate.of(2026, Month.SEPTEMBER, 20);

        assertThatThrownBy(() ->
                RouteSpecification.of(TOKYO, LOS_ANGELES, departure, deadline, LA, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("希望出発日");
    }

    @Test
    @DisplayName("出発地・目的地・到着期限が無いものは受け付けない")
    void rejectsMissingValues() {
        LocalDate deadline = LocalDate.of(2026, Month.SEPTEMBER, 20);

        assertThatThrownBy(() ->
                RouteSpecification.of(null, LOS_ANGELES, null, deadline, LA, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteSpecification.of(TOKYO, null, null, deadline, LA, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> specification(null, LA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("復元では検査しない（規則が無かったころの行が読めなくなる）")
    void restoreDoesNotValidate() {
        RouteSpecification restored = RouteSpecification.restore(
                TOKYO, TOKYO, null, LocalDate.of(2020, Month.JANUARY, 1));

        assertThat(restored.arrivalDeadline()).isEqualTo(LocalDate.of(2020, Month.JANUARY, 1));
    }

    @Test
    @DisplayName("同じ内容は等しい")
    void equality() {
        RouteSpecification one = specification(LocalDate.of(2026, Month.SEPTEMBER, 20), LA);
        RouteSpecification other = specification(LocalDate.of(2026, Month.SEPTEMBER, 20), LA);

        RouteSpecification different = specification(LocalDate.of(2026, Month.SEPTEMBER, 21), LA);

        assertThat(one)
                .isEqualTo(other)
                .hasSameHashCodeAs(other)
                .isNotEqualTo(different)
                .isNotEqualTo((Object) "spec")
                .hasToString("JPTYO → USLAX（2026-09-20 まで）");
    }

    @Nested
    @DisplayName("旅程がこの要件を満たすか（US09）")
    class SatisfiedByItinerary {

        private static final Location BUSAN = Location.of("KRPUS", "Busan");
        private static final Location OSAKA = Location.of("JPOSA", "Osaka");

        private RouteSpecification tokyoToLosAngelesBy(String deadline) {
            return RouteSpecification.of(TOKYO, LOS_ANGELES, null, LocalDate.parse(deadline),
                    LA, clock);
        }

        private CargoItinerary itinerary(Location from, Location to, String arrival) {
            return CargoItinerary.of(List.of(Leg.of(VoyageNumber.of("V0100"), from, to,
                    Instant.parse("2026-09-01T09:00:00Z"), Instant.parse(arrival))));
        }

        @Test
        @DisplayName("端点が一致し期限内に着く旅程は満たす")
        void acceptsMatchingItinerary() {
            assertThat(tokyoToLosAngelesBy("2026-09-30")
                    .isSatisfiedBy(itinerary(TOKYO, LOS_ANGELES, "2026-09-20T12:00:00Z"), LA))
                    .isTrue();
        }

        @Test
        @DisplayName("出発地が違う旅程は満たさない")
        void rejectsWrongOrigin() {
            // 大阪発の旅程を東京発の予約に割り当てると、荷主は東京で貨物を渡せない
            assertThat(tokyoToLosAngelesBy("2026-09-30")
                    .isSatisfiedBy(itinerary(OSAKA, LOS_ANGELES, "2026-09-20T12:00:00Z"), LA))
                    .isFalse();
        }

        @Test
        @DisplayName("目的地が違う旅程は満たさない")
        void rejectsWrongDestination() {
            assertThat(tokyoToLosAngelesBy("2026-09-30")
                    .isSatisfiedBy(itinerary(TOKYO, BUSAN, "2026-09-20T12:00:00Z"), LA))
                    .isFalse();
        }

        @Test
        @DisplayName("期限を過ぎて着く旅程は満たさない")
        void rejectsLateArrival() {
            assertThat(tokyoToLosAngelesBy("2026-09-30")
                    .isSatisfiedBy(itinerary(TOKYO, LOS_ANGELES, "2026-10-01T12:00:00Z"), LA))
                    .isFalse();
        }

        /**
         * 期限は日付である。「9 月 30 日まで」は「30 日中に着けばよい」を意味する。
         *
         * <p>時刻付きの到着と素朴に比較すると、期限当日に着く便を誤って刈る（IT2 の欠陥と同じ形）。
         * しかも目的地の暦で判断しないと、時差の分だけ当日が短くなる。
         */
        @Test
        @DisplayName("期限当日の遅い時刻に着く旅程も満たす")
        void acceptsArrivalLateOnTheDeadlineDay() {
            // 2026-09-30 23:00（ロサンゼルス）= 2026-10-01 06:00Z。UTC で比べると刈られる
            assertThat(tokyoToLosAngelesBy("2026-09-30")
                    .isSatisfiedBy(itinerary(TOKYO, LOS_ANGELES, "2026-10-01T06:00:00Z"), LA))
                    .isTrue();
        }

        @Test
        @DisplayName("旅程が無ければ満たさない")
        void rejectsNull() {
            assertThat(tokyoToLosAngelesBy("2026-09-30").isSatisfiedBy(null, LA)).isFalse();
        }
    }
}
