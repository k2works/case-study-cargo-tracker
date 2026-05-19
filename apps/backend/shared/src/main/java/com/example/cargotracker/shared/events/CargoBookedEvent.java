package com.example.cargotracker.shared.events;

import org.axonframework.eventsourcing.annotation.EventTag;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約登録完了イベント（shared kernel）。
 *
 * <p>bookingms から Axon Event Bus に発行され、handlingms の BookingEventAclHandler が購読する。
 * フィールドはプリミティブ型のみとし、サービス境界をまたぐ VO 依存を排除する（ADR-0014）。</p>
 */
public record CargoBookedEvent(
        @EventTag String bookingId,
        String shipperId,
        String originUnlocode,
        String destinationUnlocode,
        String cargoType,
        LocalDate arrivalDeadline,
        BigDecimal weightKg,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        Integer quantity,
        String productName,
        String hazardImoClass,
        String hazardUnNumber,
        String hazardDeclaration,
        BigDecimal temperatureMinC,
        BigDecimal temperatureMaxC) {
}
