package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/** 運送会社（コードと名称）。投影の carrier_code / carrier_name に対応する。 */
public record Carrier(String carrierCode, String carrierName) {

    public Carrier {
        if (carrierCode == null || carrierCode.isBlank()) {
            throw new BusinessRuleViolation("運送会社コードは必須です");
        }
        if (carrierName == null || carrierName.isBlank()) {
            throw new BusinessRuleViolation("運送会社名は必須です");
        }
    }
}
