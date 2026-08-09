package com.example.cargotracker.booking.domain.model;

/**
 * 危険物申告（US05）。
 *
 * <p><strong>3 項目そろって初めて申告である。</strong> どれか 1 つでも欠けると
 * 法的要件を満たさず、**申告の無い危険物を預かった**のと変わらない。
 * 部分的に入った状態を作らせないため、値オブジェクトとしてひと組で持つ。
 *
 * <p><strong>「入っている」だけでは申告にならない。</strong> 危険物クラスと UN 番号は
 * 輸送書類にそのまま載る。存在しないクラスや桁の欠けた番号を書いた書類は、
 * <strong>申告が無いのと同じ結果</strong>（積み込み拒否・税関で止まる）になる。
 *
 * @param hazardClass        危険物クラス（国連分類 1〜9。区分を持つものは {@code 5.1} の形）
 * @param unNumber           UN 番号（例: {@code UN1263}）
 * @param properShippingName 正式輸送品名（英語。輸送書類にそのまま載る）
 */
public record HazardousDeclaration(
        String hazardClass, String unNumber, String properShippingName) {

    /**
     * 国連分類のクラスと区分（実在するものだけ）。
     *
     * <p><strong>正規表現で「数字.数字」を通さない。</strong> {@code 3.9} は形は
     * それらしいが存在しない。<strong>存在しないクラスを書いた輸送書類は、
     * 申告が無いのと同じ結果になる。</strong>
     */
    private static final java.util.Set<String> HAZARD_CLASSES = java.util.Set.of(
            "1", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6",
            "2", "2.1", "2.2", "2.3",
            "3",
            "4", "4.1", "4.2", "4.3",
            "5", "5.1", "5.2",
            "6", "6.1", "6.2",
            "7", "8", "9");

    /** UN 番号は {@code UN} ＋ 4 桁。**桁が欠けた番号は別の物質を指す。** */
    private static final java.util.regex.Pattern UN_NUMBER =
            java.util.regex.Pattern.compile("UN[0-9]{4}");

    public HazardousDeclaration {
        hazardClass = trim(hazardClass);
        unNumber = trim(unNumber);
        properShippingName = trim(properShippingName);
        if (hazardClass == null) {
            throw new IllegalArgumentException("危険物クラスは必須です");
        }
        if (!HAZARD_CLASSES.contains(hazardClass)) {
            throw new IllegalArgumentException(
                    "危険物クラスは国連分類（1〜9。区分は 5.1 の形）で指定してください: "
                            + hazardClass);
        }
        if (unNumber == null) {
            throw new IllegalArgumentException("UN 番号は必須です");
        }
        // **小文字は拒まない。** 入力の揺れであって、別の物質を指すわけではない
        unNumber = unNumber.toUpperCase(java.util.Locale.ROOT);
        if (!UN_NUMBER.matcher(unNumber).matches()) {
            throw new IllegalArgumentException(
                    "UN 番号は UN に続く 4 桁で指定してください: " + unNumber);
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
     *
     * <p><strong>形式の誤りは「入力なし」に倒さない。</strong> 空を返すと呼び出し側は
     * 「入力されていない」として扱う。<strong>誤った申告を黙って捨てると、
     * 危険物が一般貨物として運ばれる。</strong>3 項目がそろっているなら、
     * 形式の誤りは例外として返す。
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
