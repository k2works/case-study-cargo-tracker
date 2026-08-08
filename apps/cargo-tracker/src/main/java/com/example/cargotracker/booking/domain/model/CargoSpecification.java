package com.example.cargotracker.booking.domain.model;

/**
 * 貨物仕様。種別・重量・寸法・個数・品名をひとまとまりで扱う。
 *
 * <p>US04 の受入基準「貨物種別・重量・寸法・個数・品名を入力できる」は、
 * 画面でもひとつの入力ブロックとして現れる（{@code ui_design.md}「貨物情報」）。
 * **5 つを個別の引数として持ち回ると、引数の順序を間違えても型が同じ限り気づけない。**
 *
 * <p><strong>種別と特別な情報の組み合わせはここが守る</strong>（US05）。
 * 「危険物なのに申告が無い」「一般貨物なのに温度条件がある」という組み合わせを作らせない。
 * <strong>DB の CHECK では書かない</strong> — 種別が増えるたびに条件が伸びて読めなくなる。
 *
 * @param cargoType   貨物種別（必須）
 * @param weight      重量（必須）
 * @param dimensions  寸法（任意。{@code null} 可）
 * @param quantity    個数（任意。{@code null} 可）
 * @param description 品名（任意。{@code null} 可）
 * @param hazardous   危険物申告。<strong>危険物では必須、それ以外では {@code null}</strong>
 * @param temperature 温度管理条件。<strong>冷凍・冷蔵では必須、それ以外では {@code null}</strong>
 */
public record CargoSpecification(
        CargoType cargoType,
        Weight weight,
        Dimensions dimensions,
        Quantity quantity,
        Description description,
        HazardousDeclaration hazardous,
        TemperatureRequirement temperature) {

    public CargoSpecification {
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (weight == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
        if (cargoType == CargoType.HAZARDOUS && hazardous == null) {
            // **申告の無い危険物を預からない。** 法的要件を満たさないまま輸送が始まる
            throw new IllegalArgumentException(
                    "危険物には危険物申告（クラス・UN 番号・正式輸送品名）が必要です");
        }
        if (cargoType == CargoType.REFRIGERATED && temperature == null) {
            throw new IllegalArgumentException(
                    "冷凍・冷蔵貨物には温度管理条件（最低温度・最高温度・単位）が必要です");
        }
        // **種別を変えた後に残った入力は捨てる。** 「危険物でないのに申告がある」形を
        // 残すと、種別で分岐する処理がどちらを信じてよいか分からなくなる
        if (cargoType != CargoType.HAZARDOUS) {
            hazardous = null;
        }
        if (cargoType != CargoType.REFRIGERATED) {
            temperature = null;
        }
    }

    /** 必須項目だけを持つ仕様（一般貨物）。 */
    public static CargoSpecification of(CargoType cargoType, Weight weight) {
        return new CargoSpecification(cargoType, weight, null, null, null, null, null);
    }

    /** 危険物申告を持つか。 */
    public boolean hasHazardousDeclaration() {
        return hazardous != null;
    }

    /** 温度管理条件を持つか。 */
    public boolean hasTemperatureRequirement() {
        return temperature != null;
    }
}
