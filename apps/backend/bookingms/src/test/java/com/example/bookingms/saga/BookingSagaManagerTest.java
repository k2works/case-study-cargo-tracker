package com.example.bookingms.saga;

import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.RouteDesignRequestedEvent;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.RouteSpecification;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
                .whenPublishingA(new RouteDesignRequestedEvent("B-001", "ROUTING"))
                .expectActiveSagas(1);
    }

    @Test
    @DisplayName("ADR-0009: 予約キャンセルイベントで Saga が終了する")
    void 予約キャンセルでSagaが終了する() {
        fixture.givenAPublished(bookedEvent("B-001"))
                .whenPublishingA(new BookingCancelledEvent("B-001", "CANCELLED"))
                .expectActiveSagas(0);
    }
}
