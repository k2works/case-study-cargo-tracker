package com.example.cargotracker.booking.domain.model;

/**
 * 危険物申告（US05）。
 *
 * <p><strong>3 項目そろって初めて申告である。</strong> どれか 1 つでも欠けると
 * 法的要件を満たさず、**申告の無い危険物を預かった**のと変わらない。
 * 部分的に入った状態を作らせないため、値オブジェクトとしてひと組で持つ。
 *
 * @param hazardClass        危険物クラス（国連分類。例: {@code 3} 引火性液体）
 * @param unNumber           UN 番号（例: {@code UN1263}）
 * @param properShippingName 正式輸送品名（英語。輸送書類にそのまま載る）
 */
public record HazardousDeclaration(
        String hazardClass, String unNumber, String properShippingName) {

    public HazardousDeclaration {
        hazardClass = trim(hazardClass);
        unNumber = trim(unNumber);
        properShippingName = trim(properShippingName);
        if (hazardClass == null) {
            throw new IllegalArgumentException("危険物クラスは必須です");
        }
        if (unNumber == null) {
            throw new IllegalArgumentException("UN 番号は必須です");
        }
        if (properShippingName == null) {
            throw new IllegalArgumentException("正式輸送品名は必須です");
        }
    }

    /**
     * 3 項目がすべて入っていれば申告を作る。1 つでも欠けていれば空を返す。
     *
     * <p><strong>「一部だけ入力された」を無かったことにしない。</strong>
     * 呼び出し側は、種別が危険物なら空を拒む（{@code CargoSpecification}）。
     */
    public static java.util.Optional<HazardousDeclaration> ofNullable(
            String hazardClass, String unNumber, String properShippingName) {
        if (trim(hazardClass) == null || trim(unNumber) == null
                || trim(properShippingName) == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new HazardousDeclaration(hazardClass, unNumber, properShippingName));
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
