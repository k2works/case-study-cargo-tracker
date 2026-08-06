package com.example.cargotracker.booking.domain.model;

/**
 * 貨物仕様。種別・重量・寸法・個数・品名をひとまとまりで扱う。
 *
 * <p>US04 の受入基準「貨物種別・重量・寸法・個数・品名を入力できる」は、
 * 画面でもひとつの入力ブロックとして現れる（{@code ui_design.md}「貨物情報」）。
 * **5 つを個別の引数として持ち回ると、引数の順序を間違えても型が同じ限り気づけない。**
 *
 * @param cargoType   貨物種別（必須）
 * @param weight      重量（必須）
 * @param dimensions  寸法（任意。{@code null} 可）
 * @param quantity    個数（任意。{@code null} 可）
 * @param description 品名（任意。{@code null} 可）
 */
public record CargoSpecification(
        CargoType cargoType,
        Weight weight,
        Dimensions dimensions,
        Quantity quantity,
        Description description) {

    public CargoSpecification {
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (weight == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
    }

    /** 必須項目だけを持つ仕様。 */
    public static CargoSpecification of(CargoType cargoType, Weight weight) {
        return new CargoSpecification(cargoType, weight, null, null, null);
    }
}
