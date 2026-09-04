package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
/** 危険物申告（IMO クラスと UN 番号）。危険物の予約には必ず添える。 */
public record HazardousDeclaration(String imoClass, String unNumber) {

    public HazardousDeclaration {
        if (imoClass == null || imoClass.isBlank()) {
            throw new BusinessRuleViolation("IMO クラスは必須です");
        }
        if (unNumber == null || unNumber.isBlank()) {
            throw new BusinessRuleViolation("UN 番号は必須です");
        }
    }
}
