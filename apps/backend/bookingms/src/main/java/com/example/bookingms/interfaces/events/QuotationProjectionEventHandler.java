package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.QuotationCreatedEvent;
import com.example.bookingms.domain.model.RouteCandidate;
import com.example.bookingms.infrastructure.repositories.mybatis.QuotationMapper;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 見積 Read Model 更新用の EventHandler（US01）。
 *
 * <p>{@link QuotationCreatedEvent} を受信して {@code quotation} に 1 行 INSERT し、
 * ルート候補を {@code quotation_candidate} に順次 INSERT する。
 * 候補が無い（期限内に到達可能な航海が無い）場合は quotation のみを INSERT する。</p>
 */
@Component
public class QuotationProjectionEventHandler {

    private final QuotationMapper quotationMapper;

    public QuotationProjectionEventHandler(QuotationMapper quotationMapper) {
        this.quotationMapper = quotationMapper;
    }

    @EventHandler
    public void on(QuotationCreatedEvent event) {
        quotationMapper.insertQuotation(
                event.quotationId(),
                event.shipperId(),
                event.routeSpec().originUnlocode(),
                event.routeSpec().destinationUnlocode(),
                event.routeSpec().arrivalDeadline(),
                event.cargoSpec().cargoType().name(),
                event.cargoSpec().weightKg(),
                event.estimatedAmount(),
                event.estimatedCurrency(),
                event.validUntil(),
                event.status()
        );
        int seq = 1;
        for (RouteCandidate candidate : event.candidateRoutes()) {
            quotationMapper.insertCandidate(
                    event.quotationId(),
                    seq++,
                    candidate.estimatedDays(),
                    candidate.estimatedCost(),
                    candidate.estimatedCurrency(),
                    candidate.itinerarySummary()
            );
        }
    }
}
