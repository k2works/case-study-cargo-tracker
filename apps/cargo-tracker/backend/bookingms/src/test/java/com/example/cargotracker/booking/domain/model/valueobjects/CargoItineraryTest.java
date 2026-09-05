package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 旅程の不変条件 4 と、経路仕様を満たすかの判断（不変条件 5）。 */
class CargoItineraryTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Instant LOAD = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant MID_UNLOAD = Instant.parse("2026-09-16T00:00:00Z");
    private static final Instant MID_LOAD = Instant.parse("2026-09-17T00:00:00Z");
    /** 業務タイムゾーンでは 2026-09-24 18:00。 */
    private static final Instant UNLOAD = Instant.parse("2026-09-24T09:00:00Z");

    private static Leg leg(String from, String to, Instant load, Instant unload) {
        return new Leg("V-MOL-001", Location.of(from), Location.of(to), load, unload);
    }

    private static CargoItinerary direct() {
        return new CargoItinerary(List.of(leg("JPTYO", "USNYC", LOAD, UNLOAD)));
    }

    private static RouteSpecification spec(LocalDate deadline) {
        return new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"), deadline);
    }

    @Test
    @DisplayName("不変条件 4: 空の旅程は作れない")
    void rejectsEmptyItinerary() {
        assertThatThrownBy(() -> new CargoItinerary(List.of()))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 4: 区間は連結していなければならない")
    void rejectsDisconnectedLegs() {
        assertThatThrownBy(() -> new CargoItinerary(List.of(
                leg("JPTYO", "SGSIN", LOAD, MID_UNLOAD),
                leg("NLRTM", "USNYC", MID_LOAD, UNLOAD))))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("連結");
    }

    @Test
    @DisplayName("不変条件 4: 前の区間の到着より前に出発する区間は作れない")
    void rejectsOutOfOrderTimes() {
        assertThatThrownBy(() -> new CargoItinerary(List.of(
                leg("JPTYO", "SGSIN", LOAD, MID_UNLOAD),
                leg("SGSIN", "USNYC", Instant.parse("2026-09-15T00:00:00Z"), UNLOAD))))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("旅程の起点と終点は最初の積地と最後の揚地")
    void exposesEndpoints() {
        CargoItinerary itinerary = new CargoItinerary(List.of(
                leg("JPTYO", "SGSIN", LOAD, MID_UNLOAD),
                leg("SGSIN", "USNYC", MID_LOAD, UNLOAD)));

        assertThat(itinerary.origin()).isEqualTo(Location.of("JPTYO"));
        assertThat(itinerary.destination()).isEqualTo(Location.of("USNYC"));
        assertThat(itinerary.finalArrival()).isEqualTo(UNLOAD);
    }

    @Test
    @DisplayName("不変条件 5: 起点・終点・期限を満たす旅程は受け入れる")
    void acceptsItineraryThatSatisfiesTheSpecification() {
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30)).isSatisfiedBy(direct(), ZONE)).isTrue();
    }

    @Test
    @DisplayName("不変条件 5: 期限当日に着く旅程は満たす（日付で比べる）")
    void arrivalOnTheDeadlineSatisfies() {
        // 時刻付きで素朴に比べると、期限当日に着く旅程を落とす。
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 24)).isSatisfiedBy(direct(), ZONE)).isTrue();
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 23)).isSatisfiedBy(direct(), ZONE)).isFalse();
    }

    @Test
    @DisplayName("不変条件 5: 起点・終点が違う旅程は満たさない")
    void wrongEndpointsDoNotSatisfy() {
        CargoItinerary fromOsaka = new CargoItinerary(
                List.of(leg("JPOSA", "USNYC", LOAD, UNLOAD)));
        CargoItinerary toLondon = new CargoItinerary(
                List.of(leg("JPTYO", "GBLON", LOAD, UNLOAD)));

        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30)).isSatisfiedBy(fromOsaka, ZONE)).isFalse();
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30)).isSatisfiedBy(toLondon, ZONE)).isFalse();
    }
}
