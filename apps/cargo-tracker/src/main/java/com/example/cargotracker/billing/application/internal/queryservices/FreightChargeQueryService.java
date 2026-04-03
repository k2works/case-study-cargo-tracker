package com.example.cargotracker.billing.application.internal.queryservices;

import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 輸送料金クエリサービス。
 */
@Service
@Transactional(readOnly = true)
public class FreightChargeQueryService {

    private final FreightChargeRepository freightChargeRepository;

    public FreightChargeQueryService(FreightChargeRepository freightChargeRepository) {
        this.freightChargeRepository = freightChargeRepository;
    }

    /**
     * 全件取得する。
     */
    public List<FreightChargeSummary> findAll() {
        return freightChargeRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * ID で取得する。
     */
    public Optional<FreightChargeSummary> findById(String id) {
        return freightChargeRepository.findById(new FreightId(UUID.fromString(id)))
                .map(this::toSummary);
    }

    private FreightChargeSummary toSummary(FreightCharge charge) {
        return new FreightChargeSummary(
                charge.getId().value().toString(),
                charge.getBookingId(),
                charge.getStatus().getDisplayName(),
                charge.getBaseAmount(),
                charge.getAdjustmentAmount(),
                charge.getTotalAmount()
        );
    }

    /**
     * 輸送料金サマリー（表示用）。
     *
     * @param status 表示用ステータス（"算出中" または "確定"）
     */
    public record FreightChargeSummary(
            String id,
            String bookingId,
            String status,
            BigDecimal baseAmount,
            BigDecimal adjustmentAmount,
            BigDecimal totalAmount
    ) {}
}
