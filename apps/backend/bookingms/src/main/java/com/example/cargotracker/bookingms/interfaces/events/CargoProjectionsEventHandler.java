package com.example.cargotracker.bookingms.interfaces.events;

import com.example.cargotracker.bookingms.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.TemperatureCondition;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryRecord;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * CargoBookedEvent 等を受け取り、cargo_summary Read Model を更新する EventHandler。
 *
 * <p>ADR-0007 採用パターン: Axon Server の Event Bus から購読し、
 * MyBatis Mapper 経由で cargo_summary テーブルを更新する。</p>
 *
 * <p>Profile 除外:</p>
 * <ul>
 *   <li>{@code springboot-integration-test}: SpringBootTest 統合テスト時。Axon Event Processor 起動を避ける</li>
 *   <li>{@code local-h2}: ローカル H2 / bootRun 時。Axon Server が未起動のため Event Processor 接続リトライ尽きを避ける</li>
 * </ul>
 *
 * <p>本 Bean が有効になるのは {@code local-docker}（Axon Server 起動済み）・{@code heroku}・{@code prod} 等のプロファイル。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class CargoProjectionsEventHandler {

    private final CargoSummaryMapper cargoSummaryMapper;

    public CargoProjectionsEventHandler(CargoSummaryMapper cargoSummaryMapper) {
        this.cargoSummaryMapper = cargoSummaryMapper;
    }

    @EventHandler
    @Transactional
    public void on(CargoBookedEvent event) {
        var record = new CargoSummaryRecord();
        record.setBookingId(event.bookingId());
        record.setShipperId(event.shipperId().value());

        var spec = event.cargoSpec();
        record.setCargoType(spec.cargoType().name());
        record.setWeightKg(spec.weightKg());
        record.setLengthCm(spec.dimensions().lengthCm());
        record.setWidthCm(spec.dimensions().widthCm());
        record.setHeightCm(spec.dimensions().heightCm());
        record.setQuantity(spec.quantity());
        record.setProductName(spec.productName());

        // 危険物・冷凍貨物の付加情報（US05 でも利用）
        HazardInfo hazard = spec.hazardInfo();
        if (hazard != null) {
            record.setHazardImoClass(hazard.imoClass());
            record.setHazardUnNumber(hazard.unNumber());
            record.setHazardDeclaration(hazard.declaration());
        }
        TemperatureCondition temp = spec.temperatureCondition();
        if (temp != null) {
            record.setTemperatureMinC(temp.minCelsius());
            record.setTemperatureMaxC(temp.maxCelsius());
        }

        var route = event.routeSpec();
        record.setOriginUnlocode(route.origin().unLocode().value());
        record.setDestinationUnlocode(route.destination().unLocode().value());
        record.setArrivalDeadline(route.arrivalDeadline());

        record.setBookingStatus(BookingStatus.PRELIMINARY.name());
        record.setRoutingStatus(RoutingStatus.NOT_ROUTED.name());

        cargoSummaryMapper.insert(record);
    }
}
