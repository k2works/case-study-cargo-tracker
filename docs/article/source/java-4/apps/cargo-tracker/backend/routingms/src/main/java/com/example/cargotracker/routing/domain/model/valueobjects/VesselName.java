package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 船名（US24 §受入基準 1 の必須入力）。
 *
 * <p>運送会社は船を複数持ち、同じ船が別の航海に就くので、船名は運送会社ではなく
 * 航海が持つ（domain-model.md）。</p>
 */
public record VesselName(String value) {

    public VesselName {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation("船名は必須です");
        }
        if (value.length() > 100) {
            throw new BusinessRuleViolation("船名は 100 文字以内です");
        }
    }
}
