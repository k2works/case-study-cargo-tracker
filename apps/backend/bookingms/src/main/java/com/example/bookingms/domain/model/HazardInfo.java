package com.example.bookingms.domain.model;

/**
 * 危険物申告情報（US05、HAZARDOUS 貨物固有）。
 *
 * <p>IMO 分類クラス・国連番号・申告文を保持する値オブジェクト。
 * Cargo Aggregate の不変条件で必須性を検証する。</p>
 *
 * @param imoClass IMO 危険物分類クラス（例: "3", "8"）
 * @param unNumber 国連番号（例: "UN1090"）
 * @param declaration 危険物申告文（取扱注意事項等）
 */
public record HazardInfo(
        String imoClass,
        String unNumber,
        String declaration
) {
}
