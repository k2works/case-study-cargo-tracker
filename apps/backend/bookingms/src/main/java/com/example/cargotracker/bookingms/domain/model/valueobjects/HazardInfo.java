package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 危険物申告情報（{@link CargoType#HAZARDOUS} 時に必須）。
 *
 * @param imoClass IMO クラス（例 "3"、"6.1"）
 * @param unNumber UN 番号（例 "1170"）
 * @param declaration 取扱注意事項の宣言文
 */
public record HazardInfo(String imoClass, String unNumber, String declaration) {

    public HazardInfo {
        Objects.requireNonNull(imoClass, "imoClass");
        Objects.requireNonNull(unNumber, "unNumber");
        Objects.requireNonNull(declaration, "declaration");
        if (imoClass.isBlank() || unNumber.isBlank() || declaration.isBlank()) {
            throw new IllegalArgumentException("HazardInfo の全項目は必須です");
        }
    }
}
