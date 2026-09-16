package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.RespondToConditionReviewCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRespondedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷主との協議の結果を経路設計者へ返す（UC08 / US10 §受入基準 4 の対）。
 *
 * <p>差し戻しは経路設計者 → 営業の一方向しか無く、<b>営業は協議を終えても伝える
 * 手段を持たなかった</b>（IT6 レビュー・IT7 引き継ぎ 2）。</p>
 */
class CargoConditionReviewResponseTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-09-06T00:00:00Z");
    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Cargo.class))
                .componentRegistry(registry -> registry.registerComponent(
                        Clock.class, c -> Clock.fixed(NOW, ZONE)));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static CargoBookedEvent booked() {
        return new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private static Object[] sentBack() {
        return new Object[] {
            booked(), new RoutingRequestedEvent("B-0001", "sales01"),
            new ConditionReviewRequestedEvent("B-0001", "期限内に着ける便がありません",
                    "routing01", EARLIER),
        };
    }

    @Test
    @DisplayName("US10 §4 の対: 差し戻された予約に協議の結果を返せる")
    void respondsToConditionReview() {
        fixture.given().events(sentBack())
                .when().command(new RespondToConditionReviewCommand("B-0001",
                        "荷主が期限を 1 月末まで延ばすことに同意", "sales01"))
                .then().success()
                .events(new ConditionReviewRespondedEvent("B-0001",
                        "荷主が期限を 1 月末まで延ばすことに同意", "sales01", NOW));
    }

    @Test
    @DisplayName("差し戻されていない予約には返せない（誰も待っていない返事を残さない）")
    void rejectsResponseWhenNotSentBack() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new RespondToConditionReviewCommand("B-0001", "決まりました",
                        "sales01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("二度は返せない（同じ差し戻しに返事が 2 つ並ばない）")
    void rejectsSecondResponse() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        new ConditionReviewRequestedEvent("B-0001", "組めません", "routing01",
                                EARLIER),
                        new ConditionReviewRespondedEvent("B-0001", "延ばします", "sales01", NOW))
                .when().command(new RespondToConditionReviewCommand("B-0001", "もう一度",
                        "sales01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("中身の無い返事は断る（経路設計者が何を直せばよいか分からない）")
    void rejectsBlankResponse() {
        fixture.given().events(sentBack())
                .when().command(new RespondToConditionReviewCommand("B-0001", "  ", "sales01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("受け付けていない予約には返せない")
    void rejectsUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(new RespondToConditionReviewCommand("B-NONE", "x", "sales01"))
                .then().exception(IllegalTransition.class);
    }
}
