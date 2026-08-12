package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 割引方針（US22）。
 *
 * <p><strong>荷主種別から「どの率を適用するか」を決める。</strong>
 * 旧版の {@code calculateRate(shipperType, amount)} は<strong>金額から</strong>
 * 割引率を算出しており、US03 / US22 が要求する「荷主ごとの契約割引率」を
 * 参照していなかった（設計レビュー H15）。契約率は
 * {@code ShipperDiscountPort} 経由で Shipper Context から取得し、
 * <strong>ここでは適用の可否だけを決める</strong>。
 *
 * <p><strong>個人荷主も同じ道を通す。</strong> 率 0% を返すのであり、
 * 計算そのものを飛ばさない。飛ばすと請求書の形が 2 種類できる。
 *
 * @param type 割引方針の種別
 */
public record DiscountPolicy(DiscountPolicyType type) {

    public DiscountPolicy {
        if (type == null) {
            throw new IllegalArgumentException("割引方針は必須です");
        }
    }

    /** 法人荷主。 */
    public static DiscountPolicy forCorporate() {
        return new DiscountPolicy(DiscountPolicyType.CORPORATE_CONTRACT);
    }

    /** 個人荷主。 */
    public static DiscountPolicy forIndividual() {
        return new DiscountPolicy(DiscountPolicyType.NONE);
    }

    /** 荷主種別（法人か）から選ぶ。**判定を呼び出し側で書き直さない。** */
    public static DiscountPolicy of(boolean corporate) {
        return corporate ? forCorporate() : forIndividual();
    }

    /**
     * 適用する割引率を決める。
     *
     * @param contractRate 契約割引率。<strong>未設定なら {@code null}</strong>
     * @return 適用する率。<strong>個人荷主・契約率なしはいずれも 0%</strong>
     */
    public DiscountRate resolveRate(DiscountRate contractRate) {
        if (type != DiscountPolicyType.CORPORATE_CONTRACT) {
            // **種別のほうが強い。** 個人荷主に契約率が付いていること自体が誤りであり、
            // 黙って適用すると誤りが請求額に化ける
            return DiscountRate.none();
        }
        if (contractRate == null) {
            // **法人でも契約率が無いことはある。** ここで例外にすると請求が止まる。
            // 割引なしで請求できることのほうが業務として正しい
            return DiscountRate.none();
        }
        return contractRate;
    }
}
