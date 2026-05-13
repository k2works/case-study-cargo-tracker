package com.example.cargotracker.bookingms.interfaces.events;

import com.example.cargotracker.bookingms.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("CargoProjectionsEventHandler")
class CargoProjectionsEventHandlerTest {

    private CargoSummaryMapper mapper;
    private CargoProjectionsEventHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(CargoSummaryMapper.class);
        handler = new CargoProjectionsEventHandler(mapper);
    }

    @Test
    @DisplayName("CargoBookedEvent を受けて cargo_summary に PRELIMINARY で INSERT する")
    void 通常貨物の登録() {
        var spec = CargoSpecification.general(
                new BigDecimal("100"), new Dimensions(100, 50, 30), 1, "産業機械");
        var route = new RouteSpecification(
                Location.of("JPYOK"), Location.of("USLAX"), LocalDate.of(2026, 12, 31));
        var event = new CargoBookedEvent("B-001", new ShipperId(7L), spec, route);

        handler.on(event);

        ArgumentCaptor<CargoSummaryRecord> captor = ArgumentCaptor.forClass(CargoSummaryRecord.class);
        verify(mapper).insert(captor.capture());
        CargoSummaryRecord r = captor.getValue();

        assertThat(r.getBookingId()).isEqualTo("B-001");
        assertThat(r.getShipperId()).isEqualTo(7L);
        assertThat(r.getCargoType()).isEqualTo("GENERAL");
        assertThat(r.getWeightKg()).isEqualByComparingTo("100");
        assertThat(r.getLengthCm()).isEqualTo(100);
        assertThat(r.getOriginUnlocode()).isEqualTo("JPYOK");
        assertThat(r.getDestinationUnlocode()).isEqualTo("USLAX");
        assertThat(r.getBookingStatus()).isEqualTo("PRELIMINARY");
        assertThat(r.getRoutingStatus()).isEqualTo("NOT_ROUTED");
        assertThat(r.getHazardImoClass()).isNull();
        assertThat(r.getTemperatureMinC()).isNull();
    }

    @Test
    @DisplayName("HAZARDOUS 貨物では HazardInfo カラムも保存される")
    void 危険物の付加情報も保存される() {
        var hazard = new HazardInfo("3", "1170", "引火性液体");
        var spec = new CargoSpecification(
                com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType.HAZARDOUS,
                new BigDecimal("50"), new Dimensions(50, 50, 50), 2, "燃料",
                hazard, null);
        var route = new RouteSpecification(
                Location.of("JPYOK"), Location.of("USLAX"), LocalDate.of(2026, 12, 31));
        var event = new CargoBookedEvent("B-002", new ShipperId(7L), spec, route);

        handler.on(event);

        ArgumentCaptor<CargoSummaryRecord> captor = ArgumentCaptor.forClass(CargoSummaryRecord.class);
        verify(mapper).insert(captor.capture());
        CargoSummaryRecord r = captor.getValue();

        assertThat(r.getCargoType()).isEqualTo("HAZARDOUS");
        assertThat(r.getHazardImoClass()).isEqualTo("3");
        assertThat(r.getHazardUnNumber()).isEqualTo("1170");
        assertThat(r.getHazardDeclaration()).isEqualTo("引火性液体");
    }
}
