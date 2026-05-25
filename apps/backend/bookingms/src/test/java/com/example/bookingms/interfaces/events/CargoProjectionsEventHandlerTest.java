package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.BookingConfirmedEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.HazardInfo;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.TemperatureCondition;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CargoProjectionsEventHandlerTest {

    @Mock
    private CargoSummaryMapper cargoSummaryMapper;

    @InjectMocks
    private CargoProjectionsEventHandler handler;

    @Test
    @DisplayName("US04: 一般貨物の CargoBookedEvent を受信すると hazard/temperature が null で INSERT する")
    void 一般貨物のCargoBookedEvent受信でhazardとtemperatureがnullでinsertされる() {
        CargoBookedEvent event = new CargoBookedEvent(
                "B-001",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "電子部品"),
                "PRELIMINARY",
                "NOT_ROUTED");

        handler.on(event);

        verify(cargoSummaryMapper).insertCargoSummary(
                "B-001",
                "S-001",
                "JPTYO",
                "USNYC",
                LocalDate.of(2026, 9, 30),
                "GENERAL",
                new BigDecimal("1500.00"),
                120,
                80,
                60,
                10,
                "電子部品",
                null,
                null,
                null,
                null,
                null,
                "PRELIMINARY",
                "NOT_ROUTED");
    }

    @Test
    @DisplayName("US05: 危険物貨物の CargoBookedEvent を受信すると hazard_* に値が設定されて INSERT する")
    void 危険物貨物のCargoBookedEvent受信でhazardカラムが設定される() {
        CargoBookedEvent event = new CargoBookedEvent(
                "B-101",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.HAZARDOUS,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "アセトン",
                        new HazardInfo("3", "UN1090", "引火性液体・直射日光厳禁"),
                        null),
                "PRELIMINARY",
                "NOT_ROUTED");

        handler.on(event);

        verify(cargoSummaryMapper).insertCargoSummary(
                "B-101",
                "S-001",
                "JPTYO",
                "USNYC",
                LocalDate.of(2026, 9, 30),
                "HAZARDOUS",
                new BigDecimal("1500.00"),
                120,
                80,
                60,
                10,
                "アセトン",
                "3",
                "UN1090",
                "引火性液体・直射日光厳禁",
                null,
                null,
                "PRELIMINARY",
                "NOT_ROUTED");
    }

    @Test
    @DisplayName("US05: 冷凍貨物の CargoBookedEvent を受信すると temperature_* に値が設定されて INSERT する")
    void 冷凍貨物のCargoBookedEvent受信でtemperatureカラムが設定される() {
        CargoBookedEvent event = new CargoBookedEvent(
                "B-102",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.REFRIGERATED,
                        new BigDecimal("2000.00"),
                        new Dimensions(150, 100, 80),
                        20,
                        "冷凍マグロ",
                        null,
                        new TemperatureCondition(new BigDecimal("-25.0"), new BigDecimal("-18.0"))),
                "PRELIMINARY",
                "NOT_ROUTED");

        handler.on(event);

        verify(cargoSummaryMapper).insertCargoSummary(
                "B-102",
                "S-001",
                "JPTYO",
                "USNYC",
                LocalDate.of(2026, 9, 30),
                "REFRIGERATED",
                new BigDecimal("2000.00"),
                150,
                100,
                80,
                20,
                "冷凍マグロ",
                null,
                null,
                null,
                new BigDecimal("-25.0"),
                new BigDecimal("-18.0"),
                "PRELIMINARY",
                "NOT_ROUTED");
    }

    @Test
    @DisplayName("US06: RouteDesignRequestedEvent 受信で booking_status が ROUTING に更新される")
    void US06_経路設計依頼イベントでbookingStatusが更新される() {
        handler.on(new RouteDesignRequestedEvent("B-001", "ROUTING"));

        verify(cargoSummaryMapper).updateBookingStatus("B-001", "ROUTING");
    }

    @Test
    @DisplayName("US13: BookingConfirmedEvent 受信で booking_status が CONFIRMED に更新される")
    void US13_予約確定イベントでbookingStatusが更新される() {
        handler.on(new BookingConfirmedEvent("B-001", "CONFIRMED"));

        verify(cargoSummaryMapper).updateBookingStatus("B-001", "CONFIRMED");
    }

    @Test
    @DisplayName("US13: BookingCancelledEvent 受信で booking_status が CANCELLED に更新される")
    void US13_予約キャンセルイベントでbookingStatusが更新される() {
        handler.on(new BookingCancelledEvent("B-001", "CANCELLED"));

        verify(cargoSummaryMapper).updateBookingStatus("B-001", "CANCELLED");
    }
}
