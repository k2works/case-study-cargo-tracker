package com.example.cargotracker.bookingms.interfaces.events;

import com.example.cargotracker.bookingms.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationCandidateRecord;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationRecord;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * QuotationCreatedEvent を購読し quotation / quotation_candidate に投影する。
 *
 * <p>CargoProjectionsEventHandler / VoyageProjectionsEventHandler と同じ {@code @Profile}
 * 制約。Axon Server 接続を要求するため {@code springboot-integration-test} と
 * {@code local-h2} では起動しない。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class QuotationProjectionsEventHandler {

    private final QuotationMapper mapper;

    public QuotationProjectionsEventHandler(QuotationMapper mapper) {
        this.mapper = mapper;
    }

    @EventHandler
    @Transactional
    public void on(QuotationCreatedEvent event) {
        // 1. quotation 本体
        var quotation = new QuotationRecord();
        quotation.setQuotationId(event.quotationId());
        quotation.setShipperId(event.shipperId().value());
        quotation.setOriginUnlocode(event.routeSpec().origin().unLocode().value());
        quotation.setDestinationUnlocode(event.routeSpec().destination().unLocode().value());
        quotation.setArrivalDeadline(event.routeSpec().arrivalDeadline());
        quotation.setCargoType(event.cargoType().name());
        quotation.setWeightKg(event.weightKg());
        quotation.setEstimatedAmount(event.estimatedAmount().amount());
        quotation.setEstimatedCurrency(event.estimatedAmount().currency());
        quotation.setValidUntil(event.validUntil());
        quotation.setStatus(event.status().name());

        // 危険物の場合は申告情報を投影（受入条件 6）
        HazardInfo hazard = event.hazardInfo();
        if (hazard != null) {
            quotation.setHazardImoClass(hazard.imoClass());
            quotation.setHazardUnNumber(hazard.unNumber());
            quotation.setHazardDeclaration(hazard.declaration());
        }

        mapper.insertQuotation(quotation);

        // 2. quotation_candidate 各行
        int seq = 1;
        for (RouteCandidate candidate : event.candidates()) {
            var candidateRecord = new QuotationCandidateRecord();
            candidateRecord.setQuotationId(event.quotationId());
            candidateRecord.setCandidateSeq(seq++);
            candidateRecord.setEstimatedDays(candidate.estimatedDays());
            candidateRecord.setEstimatedCost(candidate.estimatedAmount().amount());
            candidateRecord.setEstimatedCurrency(candidate.estimatedAmount().currency());
            candidateRecord.setItinerarySummary(candidate.itinerarySummary());
            candidateRecord.setVoyageNumbers(candidate.voyageNumbers());
            mapper.insertCandidate(candidateRecord);
        }
    }
}
