package com.example.cargotracker.billing.domain.model.services;

import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.valueobjects.FreightCalculationResult;

public class FreightCalculationService {

    public FreightCalculationResult calculate(Invoice invoice, int adjustmentAmount, String adjustmentReason) {
        return FreightCalculationResult.of(
                invoice.getBookingId(),
                invoice.getTotalAmountValue(),
                adjustmentAmount,
                adjustmentReason != null ? adjustmentReason : ""
        );
    }
}
