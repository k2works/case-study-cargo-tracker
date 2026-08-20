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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
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
        assertThatThrownBy(() -> RouteSpecification.of(
                TOKYO, TOKYO, null, LocalDate.of(2026, Month.SEPTEMBER, 20), TOKYO_ZONE, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同じにできません");
    }

    @Test
    @DisplayName("到着期限に過去の日付は指定できない")
    void rejectsPastDeadline() {
        assertThatThrownBy(() -> specification(LocalDate.of(2020, Month.JANUARY, 1), LA))
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
        assertThatThrownBy(() -> specification(LocalDate.of(2026, Month.AUGUST, 19), TOKYO_ZONE))
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
        assertThatThrownBy(() -> RouteSpecification.of(TOKYO, LOS_ANGELES,
                LocalDate.of(2026, Month.SEPTEMBER, 21), LocalDate.of(2026, Month.SEPTEMBER, 20), LA, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("希望出発日");
    }

    @Test
    @DisplayName("出発地・目的地・到着期限が無いものは受け付けない")
    void rejectsMissingValues() {
        assertThatThrownBy(() -> RouteSpecification.of(
                null, LOS_ANGELES, null, LocalDate.of(2026, Month.SEPTEMBER, 20), LA, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteSpecification.of(TOKYO, null, null,
                LocalDate.of(2026, Month.SEPTEMBER, 20), LA, clock))
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

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(one).isNotEqualTo(specification(LocalDate.of(2026, Month.SEPTEMBER, 21), LA));
        assertThat(one).isNotEqualTo("spec");
        assertThat(one).hasToString("JPTYO → USLAX（2026-09-20 まで）");
    }
}
