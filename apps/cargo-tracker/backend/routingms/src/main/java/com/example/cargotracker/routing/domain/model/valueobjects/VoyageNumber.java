package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 航海番号（Routing の識別子）。
 *
 * <p><b>BC ごとに別の型</b>にする（domain-model.md）。Handling も航海番号を持つが、
 * 意味も検査も違うので共有カーネルには置かない。</p>
 */
public record VoyageNumber(String value) {

    public VoyageNumber {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation("航海番号は必須です");
        }
        if (value.length() > 20) {
            throw new BusinessRuleViolation("航海番号は 20 文字以内です: " + value);
        }
    }
}
