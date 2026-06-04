package com.example.billingms.domain.model;

/**
 * 荷主種別（US22、bookingms 既存定義と整合）。
 *
 * <p>billingms では法人割引適用の条件として参照する。{@link CorporateDiscountPolicy} で
 * {@link #CORPORATE} のみ割引率を適用、{@link #INDIVIDUAL} は割引率 0% 強制。</p>
 */
public enum ShipperType {

    /** 法人荷主（contractRate 0〜0.30 で割引適用可）。 */
    CORPORATE,

    /** 個人荷主（割引率は常に 0%）。 */
    INDIVIDUAL
}
