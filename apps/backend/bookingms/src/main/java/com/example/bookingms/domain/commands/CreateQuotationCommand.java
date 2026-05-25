package com.example.bookingms.domain.commands;

import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.RouteCandidate;
import com.example.bookingms.domain.model.RouteSpecification;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 輸送見積作成コマンド（US01）。
 *
 * <p>{@code quotationId} で識別される見積を新規作成する。
 * ルート候補・概算金額は Application 層（QuotationService）が航海検索結果から算出して渡す。</p>
 */
public record CreateQuotationCommand(
        @TargetAggregateIdentifier String quotationId,
        String shipperId,
        RouteSpecification routeSpec,
        CargoSpecification cargoSpec,
        List<RouteCandidate> candidateRoutes,
        BigDecimal estimatedAmount,
        String estimatedCurrency,
        LocalDate validUntil
) {
}
