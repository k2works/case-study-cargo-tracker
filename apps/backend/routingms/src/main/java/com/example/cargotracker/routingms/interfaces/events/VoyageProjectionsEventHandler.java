package com.example.cargotracker.routingms.interfaces.events;

import com.example.cargotracker.routingms.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routingms.domain.model.events.VoyageScheduleUpdatedEvent;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.VoyageStatus;
import com.example.cargotracker.routingms.infrastructure.persistence.CarrierMovementRecord;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * VoyageRegisteredEvent を購読し、routing_read_db の Read Model を更新する EventHandler。
 *
 * <p>ADR-0007 ノート: {@code @EventHandler} は Axon Event Processor を自動起動し
 * Axon Server 接続を要求するため、Axon Server が未起動の profile では除外する。</p>
 *
 * <p>Profile 除外:</p>
 * <ul>
 *   <li>{@code springboot-integration-test}: SpringBootTest 統合テスト時</li>
 *   <li>{@code local-h2}: ローカル H2 / bootRun 時。Axon Server が未起動</li>
 * </ul>
 */
@Component
@Profile("!springboot-integration-test")
public class VoyageProjectionsEventHandler {

    private final VoyageMapper mapper;

    public VoyageProjectionsEventHandler(VoyageMapper mapper) {
        this.mapper = mapper;
    }

    @EventHandler
    @Transactional
    public void on(VoyageRegisteredEvent event) {
        // 1. voyage 本体
        var voyage = new VoyageRecord();
        voyage.setVoyageNumber(event.voyageNumber());
        voyage.setCarrierCode(event.carrier().code());
        voyage.setCarrierName(event.carrier().name());
        voyage.setShipName(event.shipName());
        voyage.setDepartureDate(event.departureDate());
        voyage.setArrivalDate(event.arrivalDate());
        voyage.setOriginUnlocode(event.origin().value());
        voyage.setDestinationUnlocode(event.destination().value());
        voyage.setStatus(VoyageStatus.SCHEDULED.name());
        mapper.insertVoyage(voyage);

        // 2. carrier_movement（寄港地、順序付き）
        int seq = 1;
        for (CarrierMovement movement : event.carrierMovements()) {
            var movementRecord = new CarrierMovementRecord();
            movementRecord.setVoyageNumber(event.voyageNumber());
            movementRecord.setMovementSeq(seq++);
            movementRecord.setDepartureUnlocode(movement.departure().value());
            movementRecord.setArrivalUnlocode(movement.arrival().value());
            movementRecord.setDepartureTime(movement.departureTime());
            movementRecord.setArrivalTime(movement.arrivalTime());
            mapper.insertCarrierMovement(movementRecord);
        }

        // 3. voyage_accepted_cargo_type
        for (CargoType cargoType : event.acceptedCargoTypes()) {
            mapper.insertAcceptedCargoType(event.voyageNumber(), cargoType.name());
        }
    }

    /**
     * US25: 既存航海スケジュール更新の Read Model 再投影。
     *
     * <p>voyage 本体の departure_date / arrival_date を UPDATE し、
     * carrier_movement と voyage_accepted_cargo_type は全削除→再 INSERT で
     * 差分を反映する（行数が少なく順序依存があるため delete-then-insert が単純で安全）。</p>
     */
    @EventHandler
    @Transactional
    public void on(VoyageScheduleUpdatedEvent event) {
        // 1. voyage 本体の日時を更新
        var voyage = new VoyageRecord();
        voyage.setVoyageNumber(event.voyageNumber());
        voyage.setDepartureDate(event.departureDate());
        voyage.setArrivalDate(event.arrivalDate());
        mapper.updateVoyageSchedule(voyage);

        // 2. carrier_movement を再投影
        mapper.deleteCarrierMovementsByVoyageNumber(event.voyageNumber());
        int seq = 1;
        for (CarrierMovement movement : event.carrierMovements()) {
            var movementRecord = new CarrierMovementRecord();
            movementRecord.setVoyageNumber(event.voyageNumber());
            movementRecord.setMovementSeq(seq++);
            movementRecord.setDepartureUnlocode(movement.departure().value());
            movementRecord.setArrivalUnlocode(movement.arrival().value());
            movementRecord.setDepartureTime(movement.departureTime());
            movementRecord.setArrivalTime(movement.arrivalTime());
            mapper.insertCarrierMovement(movementRecord);
        }

        // 3. voyage_accepted_cargo_type を再投影
        mapper.deleteAcceptedCargoTypesByVoyageNumber(event.voyageNumber());
        for (CargoType cargoType : event.acceptedCargoTypes()) {
            mapper.insertAcceptedCargoType(event.voyageNumber(), cargoType.name());
        }
    }
}
