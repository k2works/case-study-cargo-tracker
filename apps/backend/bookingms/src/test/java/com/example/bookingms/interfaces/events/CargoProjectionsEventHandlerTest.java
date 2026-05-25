package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.RouteSpecification;
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
    @DisplayName("US04: CargoBookedEvent を受信すると cargo_summary に INSERT する")
    void CargoBookedEvent受信でinsertCargoSummaryが呼ばれる() {
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
                "PRELIMINARY",
                "NOT_ROUTED");
    }
}
