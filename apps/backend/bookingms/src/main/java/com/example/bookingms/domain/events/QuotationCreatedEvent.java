package com.example.bookingms.domain.events;

import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.RouteCandidate;
import com.example.bookingms.domain.model.RouteSpecification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 輸送見積作成完了イベント（US01）。
 *
 * <p>初期状態の status = "DRAFT"。Read Model の quotation / quotation_candidate テーブル更新トリガーとなる。</p>
 */
public record QuotationCreatedEvent(
        String quotationId,
        String shipperId,
        RouteSpecification routeSpec,
        CargoSpecification cargoSpec,
        List<RouteCandidate> candidateRoutes,
        BigDecimal estimatedAmount,
        String estimatedCurrency,
        LocalDate validUntil,
        String status
) {
}
