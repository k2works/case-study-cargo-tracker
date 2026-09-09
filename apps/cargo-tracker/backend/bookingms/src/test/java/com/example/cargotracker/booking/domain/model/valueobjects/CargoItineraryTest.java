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
    @DisplayName("US28 §4・§5: 誤配の再設計は出発地を見ない（現在地から組み直す）")
    void redesignIgnoresTheOrigin() {
        // **予定ルートを外れた貨物は、もう出発地に無い。** 目的地は引き継ぐ。
        CargoItinerary fromSingapore = new CargoItinerary(
                List.of(leg("SGSIN", "USNYC", LOAD, UNLOAD)));

        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30))
                .isSatisfiedByRedesign(fromSingapore, Location.of("SGSIN"))).isTrue();
    }

    @Test
    @DisplayName("US28 §5: 再設計でも目的地は引き継ぐ（違う目的地は満たさない）")
    void redesignStillRequiresTheDestination() {
        CargoItinerary toLondonFromSingapore = new CargoItinerary(
                List.of(leg("SGSIN", "GBLON", LOAD, UNLOAD)));

        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30))
                .isSatisfiedByRedesign(toLondonFromSingapore, Location.of("SGSIN"))).isFalse();
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30))
                .isSatisfiedByRedesign(null, Location.of("SGSIN"))).isFalse();
    }

    @Test
    @DisplayName("US28 §6: 再設計は期限を見ない（超過は日数で数える）")
    void redesignAcceptsOverdueAndCountsTheDays() {
        // **現在地からでは間に合わないのが普通。** 断ると貨物が動かせなくなる。
        RouteSpecification tight = spec(LocalDate.of(2026, Month.SEPTEMBER, 21));

        assertThat(tight.isSatisfiedByRedesign(direct(), Location.of("JPTYO"))).isTrue();
        assertThat(tight.overdueDays(direct(), ZONE)).isEqualTo(3);
        // 間に合う旅程は 0（**組み直して間に合ったことも情報である**）。
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30))
                .overdueDays(direct(), ZONE)).isZero();
    }

    @Test
    @DisplayName("US28 §4: 再設計の起点は**誤配を検知した港**でなければならない")
    void redesignRequiresTheCurrentLocationAsOrigin() {
        // **出発地を「見ない」のではなく「差し替える」。** 検査を外すと、集約は
        // 「目的地さえ合っていればどこ発でもよい」ことになり、REST を直接叩けば
        // 貨物のいない港から出る旅程が確定できる（IT11 レビュー 高）。
        CargoItinerary fromSingapore = new CargoItinerary(
                List.of(leg("SGSIN", "USNYC", LOAD, UNLOAD)));

        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30))
                .isSatisfiedByRedesign(fromSingapore, Location.of("NLRTM")))
                .as("誤配地でない港から出る旅程は受けない").isFalse();
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 30))
                .isSatisfiedByRedesign(fromSingapore, null))
                .as("誤配地が分からない古い行では起点を検査しない").isTrue();
    }

    @Test
    @DisplayName("US28 §6: 超過日数の境界（1 日超過も数える）")
    void countsASingleOverdueDay() {
        // 0 と 2 だけだと、1 日ずらす実装に戻しても両方が偶然一致しうる。
        assertThat(spec(LocalDate.of(2026, Month.SEPTEMBER, 23)).overdueDays(direct(), ZONE))
                .isEqualTo(1);
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
