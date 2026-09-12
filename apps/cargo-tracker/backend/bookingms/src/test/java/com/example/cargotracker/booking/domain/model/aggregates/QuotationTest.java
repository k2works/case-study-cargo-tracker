package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.booking.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.EstimatedAmount;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.shared.domain.location.Location;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 輸送見積（UC01 / US01）。
 *
 * <p><b>候補 0 件でも作れることを、集約の高さで固定する。</b> 画面や REST に
 * 置くと、入口を増やすたびに同じ判断を書き直すことになる。</p>
 */
class QuotationTest {

    private static final Instant NOW = Instant.parse("2026-09-28T01:00:00Z");
    private static final String QUOTATION = "Q-0123456789abcdef0123456789abcd";

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(
                        String.class, Quotation.class))
                .componentRegistry(registry -> registry.registerComponent(Clock.class,
                        c -> Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"))));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static QuotedRoute route(String amount, int overdueDays) {
        return new QuotedRoute(
                List.of(new Leg("V-Q-001", Location.of("JPTYO"), Location.of("USNYC"),
                        Instant.parse("2026-10-01T09:00:00Z"),
                        Instant.parse("2026-10-20T18:00:00Z"))),
                20, EstimatedAmount.yen(new BigDecimal(amount)), overdueDays);
    }

    private static CreateQuotationCommand create(CargoType cargoType,
            HazardousDeclaration hazardous, List<QuotedRoute> candidates) {
        return new CreateQuotationCommand(QUOTATION,
                new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                        LocalDate.of(2026, 12, 1)),
                cargoType, Weight.ofKilograms("1200"), hazardous, candidates, "sales01");
    }

    private QuotationCreatedEvent createdEventOf(CreateQuotationCommand command) {
        var captured = new QuotationCreatedEvent[1];
        fixture.given().noPriorActivity()
                .when().command(command)
                .then().eventsSatisfy(events -> captured[0] = events.stream()
                        .map(event -> event.payload())
                        .filter(QuotationCreatedEvent.class::isInstance)
                        .map(QuotationCreatedEvent.class::cast)
                        .findFirst().orElseThrow());
        return captured[0];
    }

    @Test
    @DisplayName("US01 §4: 見積を作ると 5 項目と候補が残り、有効期限が決まる")
    void createsTheQuotation() {
        QuotationCreatedEvent event = createdEventOf(
                create(CargoType.GENERAL, null, List.of(route("510000", 0))));

        assertThat(event.quotationId()).isEqualTo(QUOTATION);
        assertThat(event.originUnLocode()).isEqualTo("JPTYO");
        assertThat(event.destinationUnLocode()).isEqualTo("USNYC");
        assertThat(event.arrivalDeadline()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(event.cargoType()).isEqualTo("GENERAL");
        assertThat(event.weightKg()).isEqualByComparingTo("1200");
        // 業務タイムゾーン（Asia/Tokyo）の 2026-09-28 + 30 日。
        assertThat(event.validUntil()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(event.candidates()).singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.voyageNumbers()).isEqualTo("V-Q-001");
                    assertThat(candidate.estimatedCost()).isEqualByComparingTo("510000");
                });
    }

    @Test
    @DisplayName("US01 §5: 候補 0 件でも見積は作れる（概算は 0 円）")
    void createsAQuotationWithoutCandidates() {
        QuotationCreatedEvent event = createdEventOf(
                create(CargoType.GENERAL, null, List.of()));

        assertThat(event.candidates())
                .as("「期限に間に合う経路がありません」も荷主に返すべき答え")
                .isEmpty();
        assertThat(event.estimatedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("概算は期限に間に合う候補のいちばん安いもの（間に合わない候補が安くても選ばない）")
    void picksTheCheapestCandidateThatMeetsTheDeadline() {
        QuotationCreatedEvent event = createdEventOf(create(CargoType.GENERAL, null,
                List.of(route("510000", 0), route("300000", 5))));

        assertThat(event.estimatedAmount())
                .as("安いほうを示すと「その額では間に合わない」と伝えそこねる")
                .isEqualByComparingTo("510000");
    }

    @Test
    @DisplayName("間に合う候補が 1 件も無ければ、間に合わない候補の中から選ぶ")
    void fallsBackToLateCandidates() {
        QuotationCreatedEvent event = createdEventOf(create(CargoType.GENERAL, null,
                List.of(route("510000", 3), route("300000", 5))));

        assertThat(event.estimatedAmount()).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("不変条件 1: 出発地と目的地が同じ見積は作れない")
    void refusesTheSameOriginAndDestination() {
        // 経路仕様そのものが断る（集約に届く前）。ここでは型の検査として確かめる。
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new RouteSpecification(Location.of("JPTYO"), Location.of("JPTYO"),
                        LocalDate.of(2026, 12, 1)))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("危険物には危険物申告が要る（予約で初めて断られて出し直しにしない）")
    void refusesHazardousWithoutDeclaration() {
        fixture.given().noPriorActivity()
                .when().command(create(CargoType.HAZARDOUS, null, List.of()))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("経路・貨物種別・重量が欠けた見積は作れない（5 項目が見積の中身）")
    void refusesMissingTerms() {
        fixture.given().noPriorActivity()
                .when().command(new CreateQuotationCommand(QUOTATION, null,
                        CargoType.GENERAL, Weight.ofKilograms("1200"), null, List.of(),
                        "sales01"))
                .then().exception(BusinessRuleViolation.class);

        fixture.given().noPriorActivity()
                .when().command(new CreateQuotationCommand(QUOTATION,
                        new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                                LocalDate.of(2026, 12, 1)),
                        null, Weight.ofKilograms("1200"), null, List.of(), "sales01"))
                .then().exception(BusinessRuleViolation.class);

        fixture.given().noPriorActivity()
                .when().command(new CreateQuotationCommand(QUOTATION,
                        new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                                LocalDate.of(2026, 12, 1)),
                        CargoType.GENERAL, null, null, List.of(), "sales01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("候補の一覧そのものが無くても作れる（0 件と同じに扱う）")
    void createsWithoutACandidateList() {
        var created = createdEventOf(new CreateQuotationCommand(QUOTATION,
                new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                        LocalDate.of(2026, 12, 1)),
                CargoType.GENERAL, Weight.ofKilograms("1200"), null, null, "sales01"));

        assertThat(created.candidates()).isEmpty();
        assertThat(created.estimatedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("候補が null のイベントでも復元できる（古い記録を読めなくしない）")
    void restoresFromAnEventWithoutCandidates() {
        var created = new QuotationCreatedEvent(QUOTATION, "JPTYO", "USNYC",
                LocalDate.of(2026, 12, 1), "GENERAL", new BigDecimal("1200"), null, null,
                BigDecimal.ZERO, "JPY", LocalDate.of(2026, 10, 28), null, "sales01",
                Instant.parse("2026-09-28T01:00:00Z"));

        assertThat(created.candidates())
                .as("復元では断らず既定に落とす（追記専用の形を守る）")
                .isEmpty();
    }

    @Test
    @DisplayName("作られていない見積は予約と比べられない（比べる相手が無い）")
    void refusesToDiffWithoutAQuotation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new Quotation().diffAgainst(bookingWith("USNYC", CargoType.GENERAL, "1200")))
                .isInstanceOf(IllegalTransition.class);
    }

    @Test
    @DisplayName("同じ見積番号で二度作れない")
    void doesNotCreateTwice() {
        var created = createdEventOf(create(CargoType.GENERAL, null, List.of()));

        fixture.given().event(created)
                .when().command(create(CargoType.GENERAL, null, List.of()))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("見積 ID が列の長さ（36 文字）を超えたら断る（投影だけが静かに退避されるのを防ぐ）")
    void refusesAnOverlongIdentifier() {
        String tooLong = "Q-" + "0123456789abcdef0123456789abcdef0123456789";
        assertThat(tooLong.length()).isGreaterThan(36);

        fixture.given().noPriorActivity()
                .when().command(new CreateQuotationCommand(tooLong,
                        new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                                LocalDate.of(2026, 12, 1)),
                        CargoType.GENERAL, Weight.ofKilograms("1200"), null,
                        List.of(), "sales01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 3: 予約との違いを断らずに項目名で返す（「何から何へ」まで）")
    void reportsDifferencesAgainstTheBooking() {
        var created = createdEventOf(create(CargoType.GENERAL, null, List.of()));

        // **集約を復元してから聞く。** 復元のハンドラが項目を写していなければ、
        // ここで違いを数えられない。
        Quotation quotation = restored(created);

        List<String> differences = quotation.diffAgainst(bookingWith(
                "USLAX", CargoType.GENERAL, "1500"));

        assertThat(differences)
                .as("項目名だけでは、営業担当者は見積を開き直して見比べることになる")
                .anySatisfy(difference ->
                        assertThat(difference).contains("目的地").contains("USNYC")
                                .contains("USLAX"))
                .anySatisfy(difference ->
                        assertThat(difference).contains("重量").contains("1200")
                                .contains("1500"));
    }

    @Test
    @DisplayName("見積どおりの予約では違いが出ない（1200 と 1200.00 を「違う」と言わない）")
    void reportsNoDifferenceForAMatchingBooking() {
        var created = createdEventOf(create(CargoType.GENERAL, null, List.of()));

        assertThat(restored(created).diffAgainst(
                bookingWith("USNYC", CargoType.GENERAL, "1200.00")))
                .as("毎回「違う」と出ると読まれなくなる")
                .isEmpty();
    }

    @Test
    @DisplayName("候補が 1 件でもあれば hasCandidate は真（画面に数え直させない）")
    void hasCandidateWhenAtLeastOneExists() {
        // **1 つのテストでフィクスチャを 2 度使わない。** given() は集約の状態を
        // 持ち越すので、2 度目のコマンドが「すでに作られています」で断られる。
        assertThat(restored(createdEventOf(
                create(CargoType.GENERAL, null, List.of(route("510000", 0))))).hasCandidate())
                .isTrue();
    }

    @Test
    @DisplayName("候補が 0 件なら hasCandidate は偽")
    void hasNoCandidateWhenTheListIsEmpty() {
        assertThat(restored(createdEventOf(
                create(CargoType.GENERAL, null, List.of()))).hasCandidate())
                .isFalse();
    }

    private static BookCargoCommand bookingWith(String destination, CargoType cargoType,
            String weightKg) {
        return new BookCargoCommand("B-1", "SHP-000001",
                new CargoSpecification(cargoType, Weight.ofKilograms(weightKg),
                        new Dimensions(new BigDecimal("120"), new BigDecimal("80"),
                                new BigDecimal("100")),
                        10, "自動車部品", null, null),
                new RouteSpecification(Location.of("JPTYO"), Location.of(destination),
                        LocalDate.of(2026, 12, 1)),
                "sales01");
    }

    /**
     * イベント列から集約を復元する。
     *
     * <p><b>本番と同じ復元経路を通す。</b> フィールドを直接組み立てると、
     * {@code @EventSourcingHandler} の書き漏らしを素通りさせる。</p>
     */
    private static Quotation restored(Object... events) {
        Quotation quotation = new Quotation();
        for (Object event : events) {
            applyTo(quotation, event);
        }
        return quotation;
    }

    private static void applyTo(Quotation quotation, Object event) {
        for (var method : Quotation.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(
                    org.axonframework.eventsourcing.annotation.EventSourcingHandler.class)) {
                continue;
            }
            var parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0].isInstance(event)) {
                method.setAccessible(true);
                try {
                    method.invoke(quotation, event);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("復元できません: " + event, e);
                }
                return;
            }
        }
        throw new IllegalStateException("復元のハンドラがありません: " + event.getClass());
    }
}
