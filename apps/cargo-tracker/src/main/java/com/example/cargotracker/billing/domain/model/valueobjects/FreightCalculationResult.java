package com.example.cargotracker.billing.domain.model.valueobjects;

public record FreightCalculationResult(
        String bookingId,
        int baseFreight,
        int adjustmentAmount,
        String adjustmentReason,
        int finalAmount
) {

    public static FreightCalculationResult of(
            String bookingId,
            int baseFreight,
            int adjustmentAmount,
            String adjustmentReason
    ) {
        return new FreightCalculationResult(
                bookingId,
                baseFreight,
                adjustmentAmount,
                adjustmentReason,
                baseFreight + adjustmentAmount
        );
    }
}
