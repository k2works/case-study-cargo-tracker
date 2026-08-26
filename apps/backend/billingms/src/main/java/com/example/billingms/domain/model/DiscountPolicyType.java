package com.example.billingms.domain.model;

/**
 * 割引方針の種別（[ADR-027] 注 10）。
 *
 * <p><strong>本 IT で実装するのは 2 値だけである。</strong>正典（domain-model.md）には
 * {@code VOLUME_DISCOUNT} / {@code SEASONAL} も定義があるが、US22 の受入基準に無く、
 * <strong>決める相手（契約条件）がいない</strong>。
 *
 * <p>宣言だけしても、算定に使われないまま {@code switch} が網羅していることになる
 * ——値が「業務として空」のまま残る形は IT10 で踏んだ（Problem 3）。US23 以降で
 * 契約条件が決まったときに足す。
 */
public enum DiscountPolicyType {

    /** 法人契約の標準割引。荷主に登録された率をそのまま使う（US22）。 */
    CORPORATE_STANDARD,

    /**
     * 割引なし。
     *
     * <p>個人荷主、または法人でも割引率が未設定のとき。
     * <strong>0% ではなく「無い」</strong>——0% を出すと、契約が無いことと区別できない。
     */
    NONE
}
