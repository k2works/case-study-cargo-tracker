package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 追跡番号（trackingms の型）。
 *
 * <p><b>bookingms の {@code TrackingNumber} とは別の型にします</b>（domain-model.md
 * 「置かないもの」に識別子が挙がっている）。契約では文字列で運び、受け取った側が
 * 自分の型へ組み直します。共有すると、片方の BC が採番規則を変えたときにもう一方が
 * 巻き込まれます。</p>
 *
 * <p><b>採番しません。</b> 番号は bookingms の投影が採り、契約コマンドで届きます。</p>
 */
public record TrackingNumber(String value) {

    public TrackingNumber {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation("追跡番号は必須です");
        }
        value = value.trim();
    }

    public static TrackingNumber of(String value) {
        return new TrackingNumber(value);
    }
}
