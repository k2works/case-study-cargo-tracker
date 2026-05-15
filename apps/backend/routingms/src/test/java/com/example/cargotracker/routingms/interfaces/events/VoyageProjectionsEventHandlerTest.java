package com.example.cargotracker.routingms.interfaces.events;

import com.example.cargotracker.routingms.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routingms.domain.model.events.VoyageScheduleUpdatedEvent;
import com.example.cargotracker.routingms.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;
import com.example.cargotracker.routingms.infrastructure.persistence.CarrierMovementRecord;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("VoyageProjectionsEventHandler")
class VoyageProjectionsEventHandlerTest {

    private VoyageMapper mapper;
    private VoyageProjectionsEventHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(VoyageMapper.class);
        handler = new VoyageProjectionsEventHandler(mapper);
    }

    @Test
    @DisplayName("VoyageRegisteredEvent を受けて voyage / carrier_movement / accepted_cargo_type に INSERT する")
    void 投影で全テーブルにINSERTされる() {
        var movements = List.of(
                new CarrierMovement(new UnLocode("JPYOK"), new UnLocode("TWKHH"),
                        LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 5, 18, 0)),
                new CarrierMovement(new UnLocode("TWKHH"), new UnLocode("USLAX"),
                        LocalDateTime.of(2026, 7, 6, 9, 0), LocalDateTime.of(2026, 7, 15, 18, 0)));
        var event = new VoyageRegisteredEvent(
                "V12345",
                new Carrier("MOL", "Mitsui O.S.K. Lines"),
                "Yokohama Express",
                new UnLocode("JPYOK"),
                new UnLocode("USLAX"),
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 15, 18, 0),
                movements,
                List.of(CargoType.GENERAL, CargoType.REFRIGERATED));

        handler.on(event);

        // voyage 本体
        ArgumentCaptor<VoyageRecord> voyageCaptor = ArgumentCaptor.forClass(VoyageRecord.class);
        verify(mapper).insertVoyage(voyageCaptor.capture());
        VoyageRecord v = voyageCaptor.getValue();
        assertThat(v.getVoyageNumber()).isEqualTo("V12345");
        assertThat(v.getCarrierCode()).isEqualTo("MOL");
        assertThat(v.getShipName()).isEqualTo("Yokohama Express");
        assertThat(v.getOriginUnlocode()).isEqualTo("JPYOK");
        assertThat(v.getStatus()).isEqualTo("SCHEDULED");

        // carrier_movement 2 件
        ArgumentCaptor<CarrierMovementRecord> movementCaptor = ArgumentCaptor.forClass(CarrierMovementRecord.class);
        verify(mapper, times(2)).insertCarrierMovement(movementCaptor.capture());
        var captured = movementCaptor.getAllValues();
        assertThat(captured.get(0).getMovementSeq()).isEqualTo(1);
        assertThat(captured.get(0).getDepartureUnlocode()).isEqualTo("JPYOK");
        assertThat(captured.get(1).getMovementSeq()).isEqualTo(2);
        assertThat(captured.get(1).getDepartureUnlocode()).isEqualTo("TWKHH");

        // accepted_cargo_type 2 件
        verify(mapper).insertAcceptedCargoType("V12345", "GENERAL");
        verify(mapper).insertAcceptedCargoType("V12345", "REFRIGERATED");
    }

    @Test
    @DisplayName("US25: VoyageScheduleUpdatedEvent を受けて voyage を更新し movement / cargo_type を再投影する")
    void 更新イベントで再投影される() {
        var newMovements = List.of(
                new CarrierMovement(new UnLocode("JPYOK"), new UnLocode("TWKHH"),
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 7, 18, 0)),
                new CarrierMovement(new UnLocode("TWKHH"), new UnLocode("USLAX"),
                        LocalDateTime.of(2026, 7, 8, 9, 0), LocalDateTime.of(2026, 7, 17, 18, 0)));
        var event = new VoyageScheduleUpdatedEvent(
                "V12345",
                LocalDateTime.of(2026, 7, 3, 9, 0),
                LocalDateTime.of(2026, 7, 17, 18, 0),
                newMovements,
                List.of(CargoType.GENERAL, CargoType.HAZARDOUS));

        handler.on(event);

        // 1. voyage 本体を update
        ArgumentCaptor<VoyageRecord> voyageCaptor = ArgumentCaptor.forClass(VoyageRecord.class);
        verify(mapper).updateVoyageSchedule(voyageCaptor.capture());
        VoyageRecord v = voyageCaptor.getValue();
        assertThat(v.getVoyageNumber()).isEqualTo("V12345");
        assertThat(v.getDepartureDate()).isEqualTo(LocalDateTime.of(2026, 7, 3, 9, 0));
        assertThat(v.getArrivalDate()).isEqualTo(LocalDateTime.of(2026, 7, 17, 18, 0));

        // 2. 既存 carrier_movement を削除して再 INSERT
        verify(mapper).deleteCarrierMovementsByVoyageNumber("V12345");
        ArgumentCaptor<CarrierMovementRecord> movementCaptor = ArgumentCaptor.forClass(CarrierMovementRecord.class);
        verify(mapper, times(2)).insertCarrierMovement(movementCaptor.capture());
        var captured = movementCaptor.getAllValues();
        assertThat(captured.get(0).getMovementSeq()).isEqualTo(1);
        assertThat(captured.get(0).getDepartureUnlocode()).isEqualTo("JPYOK");
        assertThat(captured.get(1).getMovementSeq()).isEqualTo(2);

        // 3. 既存 accepted_cargo_type を削除して再 INSERT
        verify(mapper).deleteAcceptedCargoTypesByVoyageNumber("V12345");
        verify(mapper).insertAcceptedCargoType("V12345", "GENERAL");
        verify(mapper).insertAcceptedCargoType("V12345", "HAZARDOUS");
    }
}
