package com.example.cargotracker.handling.domain.model;

/**
 * 申告番号（US29 の業務キー）。
 *
 * <p><strong>税関から与えられる番号であり、こちらでは採番しない</strong>（ADR-006 の下でも
 * 変わらない。現実の運用では税関の受付番号を書き写す）。そのため形式は縛らず、
 * <strong>空でないこと</strong>だけを守る。
 *
 * @param value 申告番号
 */
public record DeclarationNumber(String value) {

    public DeclarationNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("申告番号は必須です");
        }
        value = value.trim();
        if (value.length() > 50) {
            throw new IllegalArgumentException("申告番号は 50 文字以内です");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
