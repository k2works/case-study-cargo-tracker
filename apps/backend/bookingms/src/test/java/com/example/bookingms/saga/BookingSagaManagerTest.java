package com.example.bookingms.saga;

import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RouteSpecification;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

class BookingSagaManagerTest {

    private SagaTestFixture<BookingSagaManager> fixture;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(BookingSagaManager.class);
    }

    private CargoBookedEvent bookedEvent(String bookingId) {
        return new CargoBookedEvent(
                bookingId, "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(CargoType.GENERAL, new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60), 10, "電子部品"),
                "PRELIMINARY", "NOT_ROUTED");
    }

    @Test
    @DisplayName("ADR-0009: 予約登録イベントで Saga が開始する")
    void 予約登録でSagaが開始する() {
        fixture.givenNoPriorActivity()
                .whenPublishingA(bookedEvent("B-001"))
                .expectActiveSagas(1);
    }

    @Test
    @DisplayName("ADR-0009: 経路設計依頼イベントでも Saga は継続する")
    void 経路設計依頼でSagaが継続する() {
        fixture.givenAPublished(bookedEvent("B-001"))
                .whenPublishingA(new RouteDesignRequestedEvent("B-001", "ROUTING", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL"))
                .expectActiveSagas(1);
    }

    @Test
    @DisplayName("ADR-0009: 予約キャンセルイベントで Saga が終了する")
    void 予約キャンセルでSagaが終了する() {
        fixture.givenAPublished(bookedEvent("B-001"))
                .whenPublishingA(new BookingCancelledEvent("B-001", "CANCELLED"))
                .expectActiveSagas(0);
    }

    @Test
    @DisplayName("US11: 経路確定イベントでも Saga は継続する（経路提案中）")
    void 経路確定でSagaが継続する() {
        fixture.givenAPublished(bookedEvent("B-001"))
                .andThenAPublished(new RouteDesignRequestedEvent(
                        "B-001", "ROUTING", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL"))
                .whenPublishingA(new CargoRoutedEvent("B-001", "ROUTE_PROPOSED", "ROUTED", List.of(
                        new Leg("V-100", "JPTYO", "USNYC",
                                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0)))))
                .expectActiveSagas(1);
    }
}
