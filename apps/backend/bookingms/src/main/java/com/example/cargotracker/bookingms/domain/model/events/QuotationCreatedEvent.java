package com.example.cargotracker.bookingms.domain.model.events;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Money;
import com.example.cargotracker.bookingms.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積作成完了イベント（US01 / UC01）。
 *
 * <p>{@code Quotation} 集約が {@code CreateQuotationCommand} を処理して発行する。
 * Projection EventHandler が {@code quotation} / {@code quotation_candidate} に投影する。</p>
 *
 * <p>{@code candidates} が空の場合は受入条件 5「期限内ルートが存在しない」を意味する。</p>
 */
public record QuotationCreatedEvent(
        String quotationId,
        ShipperId shipperId,
        RouteSpecification routeSpec,
        CargoType cargoType,
        BigDecimal weightKg,
        Money estimatedAmount,
        List<RouteCandidate> candidates,
        LocalDate validUntil,
        QuotationStatus status) {
}
