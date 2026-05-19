package com.example.cargotracker.bookingms.interfaces.events;

import com.example.cargotracker.bookingms.domain.model.events.CargoHandedOffToRoutingEvent;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryRecord;
import com.example.cargotracker.shared.events.CargoBookedEvent;
import com.example.cargotracker.shared.events.CargoRoutedEvent;
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
        var event = new CargoBookedEvent("B-001", "7", "JPYOK", "USLAX", "GENERAL",
                LocalDate.of(2026, 12, 31), new BigDecimal("100"),
                100, 50, 30, 1, "産業機械", null, null, null, null, null);

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
        var event = new CargoBookedEvent("B-002", "7", "JPYOK", "USLAX", "HAZARDOUS",
                LocalDate.of(2026, 12, 31), new BigDecimal("50"),
                50, 50, 50, 2, "燃料", "3", "1170", "引火性液体", null, null);

        handler.on(event);

        ArgumentCaptor<CargoSummaryRecord> captor = ArgumentCaptor.forClass(CargoSummaryRecord.class);
        verify(mapper).insert(captor.capture());
        CargoSummaryRecord r = captor.getValue();

        assertThat(r.getCargoType()).isEqualTo("HAZARDOUS");
        assertThat(r.getHazardImoClass()).isEqualTo("3");
        assertThat(r.getHazardUnNumber()).isEqualTo("1170");
        assertThat(r.getHazardDeclaration()).isEqualTo("引火性液体");
    }

    @Test
    @DisplayName("REFRIGERATED 貨物では TemperatureCondition カラムも保存される（US05）")
    void 冷凍貨物の温度条件も保存される() {
        var event = new CargoBookedEvent("B-003", "7", "JPYOK", "USLAX", "REFRIGERATED",
                LocalDate.of(2026, 12, 31), new BigDecimal("200"),
                150, 80, 80, 1, "冷凍マグロ", null, null, null,
                new BigDecimal("-25"), new BigDecimal("-18"));

        handler.on(event);

        ArgumentCaptor<CargoSummaryRecord> captor = ArgumentCaptor.forClass(CargoSummaryRecord.class);
        verify(mapper).insert(captor.capture());
        CargoSummaryRecord r = captor.getValue();

        assertThat(r.getCargoType()).isEqualTo("REFRIGERATED");
        assertThat(r.getTemperatureMinC()).isEqualByComparingTo("-25");
        assertThat(r.getTemperatureMaxC()).isEqualByComparingTo("-18");
        assertThat(r.getHazardImoClass()).isNull();
    }

    @Test
    @DisplayName("CargoHandedOffToRoutingEvent を受けて cargo_summary.booking_status を ROUTING に更新する（US06）")
    void 経路設計引き渡しでROUTINGに更新() {
        handler.on(new CargoHandedOffToRoutingEvent("B-100"));

        verify(mapper).updateBookingStatus("B-100", "ROUTING");
    }

    @Test
    @DisplayName("US11: CargoRoutedEvent を受けて cargo_summary.booking_status を ROUTE_PROPOSED に更新する")
    void 経路紐付けでROUTE_PROPOSEDに更新() {
        handler.on(new CargoRoutedEvent("B-200"));

        verify(mapper).updateBookingStatus("B-200", "ROUTE_PROPOSED");
    }
}
