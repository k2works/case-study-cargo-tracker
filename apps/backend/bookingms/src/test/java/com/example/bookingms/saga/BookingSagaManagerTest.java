package com.example.bookingms.saga;

import com.example.bookingms.domain.commands.AssignTrackingDetailsCommand;
import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.BookingConfirmedEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.shared.events.CargoTrackedEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import com.example.shared.events.TrackingIssuanceRequestedEvent;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RouteSpecification;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingSagaManagerTest {

    private SagaTestFixture<BookingSagaManager> fixture;
    private EventGateway eventGateway;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(BookingSagaManager.class);
        eventGateway = mock(EventGateway.class);
        fixture.registerResource(eventGateway);
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

    @Test
    @DisplayName("US14 / IT5 1.2: 予約確定で TrackingIssuanceRequestedEvent が cross-service publish される")
    void 予約確定で追跡発行依頼イベントが発行される() {
        // 予約確定（CONFIRMED）後、Saga が RouteDesignRequestedEvent と CargoRoutedEvent から
        // 集約した属性（出発地・目的地・到着期限・貨物種別・確定旅程）を使って
        // shared モジュールの TrackingIssuanceRequestedEvent を EventGateway 経由で publish する。
        // 受信側 trackingms は InitializeTrackingCommand を発行して TrackingActivity を
        // NOT_RECEIVED 初期化・採番する（IT5 1.3）。
        LocalDate deadline = LocalDate.of(2026, 9, 30);
        LocalDateTime loadTime = LocalDateTime.of(2026, 7, 3, 9, 0);
        LocalDateTime unloadTime = LocalDateTime.of(2026, 7, 28, 18, 0);

        fixture.givenAPublished(bookedEvent("B-001"))
                .andThenAPublished(new RouteDesignRequestedEvent(
                        "B-001", "ROUTING", "JPTYO", "USNYC", deadline, "GENERAL"))
                .andThenAPublished(new CargoRoutedEvent("B-001", "ROUTE_PROPOSED", "ROUTED", List.of(
                        new Leg("V-100", "JPTYO", "USNYC", loadTime, unloadTime))))
                .whenPublishingA(new BookingConfirmedEvent("B-001", "CONFIRMED"))
                .expectActiveSagas(1);

        verify(eventGateway).publish(eq(new TrackingIssuanceRequestedEvent(
                "B-001", "JPTYO", "USNYC", deadline, "GENERAL",
                List.of(new TrackingIssuanceRequestedEvent.LegData(
                        "V-100", "JPTYO", "USNYC", loadTime, unloadTime))
        )));
    }

    @Test
    @DisplayName("US14 / IT5 1.2: 別 bookingId の予約確定では Saga は新規開始しない（association 境界）")
    void 別IDの予約確定では新規Sagaが開始しない() {
        // BookingConfirmedEvent は @StartSaga ではないため、未開始の bookingId に対して
        // 単独で publish しても Saga は活性化しない。association が正しく "bookingId" に紐づき、
        // 別 ID で誤って新規 Saga が起動しないことを担保する（CargoBookedEvent のみが @StartSaga）。
        fixture.givenAPublished(bookedEvent("B-001"))
                .whenPublishingA(new BookingConfirmedEvent("B-002", "CONFIRMED"))
                .expectActiveSagas(1);  // B-001 のまま、B-002 で新規開始しない
    }

    @Test
    @DisplayName("US14 / IT5 1.4: CargoTrackedEvent（採番完了）で AssignTrackingDetailsCommand を送信し Saga が終了する")
    void 採番完了で追跡情報割当コマンドを送信しSagaが終了する() {
        // trackingms から CargoTrackedEvent を Kafka 経由で受信すると、Cargo 集約に
        // AssignTrackingDetailsCommand を送信して予約状態を TRACKING_ISSUED に遷移させ、
        // @EndSaga で Saga を終了する。予約確定→追跡発行依頼→trackingms 採番→
        // bookingms 完了通知→Cargo 状態更新 のサイクルが完結する。
        fixture.givenAPublished(bookedEvent("B-001"))
                .whenPublishingA(new CargoTrackedEvent("B-001", "TRK-AB12CD3456"))
                .expectActiveSagas(0)
                .expectDispatchedCommands(
                        new AssignTrackingDetailsCommand("B-001", "TRK-AB12CD3456"));
    }
}
