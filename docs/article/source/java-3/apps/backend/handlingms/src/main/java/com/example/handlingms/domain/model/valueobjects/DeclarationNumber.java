package com.example.handlingms.domain.model.valueobjects;

/**
 * 申告番号（[ADR-025] 決定 8）。
 *
 * <p><strong>値オブジェクトにするのは、破りうる規則があるからである</strong>（[ADR-012]）。
 * 申告番号は税関から受け取る業務キーであり、空では申告を特定できない。
 *
 * <p><strong>書式までは検査しない。</strong>採番するのは税関であり、こちらではない。
 * 書式を決め打つと、様式が変わった日に受け付けられなくなる。
 *
 * @param value 申告番号の文字列
 */
public record DeclarationNumber(String value) {

    /** 列の長さ（`data-model.md` の `VARCHAR(50)`）。**入り切らない値を保存時まで持ち越さない**。 */
    private static final int MAX_LENGTH = 50;

    public static DeclarationNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("申告番号は必須です");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "申告番号は %d 文字以内で入力してください".formatted(MAX_LENGTH));
        }
        return new DeclarationNumber(trimmed);
    }

    @Override
    public String toString() {
        return value;
    }
}
