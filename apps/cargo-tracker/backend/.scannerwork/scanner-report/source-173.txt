package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 運送会社（コードと名称）。投影の carrier_code / carrier_name に対応する。
 *
 * <p>長さは投影の列に合わせる。ここで断らないと、集約を通ったイベントが投影で
 * 落ちて Processing Group が止まる（利用者には「登録したのに一覧に出ない」に見える）。</p>
 */
public record Carrier(String carrierCode, String carrierName) {

    public Carrier {
        if (carrierCode == null || carrierCode.isBlank()) {
            throw new BusinessRuleViolation("運送会社コードは必須です");
        }
        if (carrierCode.length() > 20) {
            throw new BusinessRuleViolation("運送会社コードは 20 文字以内です: " + carrierCode);
        }
        if (carrierName == null || carrierName.isBlank()) {
            throw new BusinessRuleViolation("運送会社名は必須です");
        }
        if (carrierName.length() > 100) {
            throw new BusinessRuleViolation("運送会社名は 100 文字以内です");
        }
    }
}
