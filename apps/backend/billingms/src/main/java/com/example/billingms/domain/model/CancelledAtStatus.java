package com.example.billingms.domain.model;

import java.math.BigDecimal;

/**
 * キャンセルを申請した時点の予約の状態（US30-9・正典のビジネスルール 6）。
 *
 * <p><strong>bookingms の {@code BookingStatus}（8 値）とは別の型である。</strong>
 * こちらが持つのは<strong>キャンセルできる 6 値だけ</strong>——配送完了とキャンセル済みからは
 * キャンセルできない。全 8 値を持つと、<strong>すでに運び終えた貨物にキャンセル料が乗る</strong>
 * 経路ができてしまう。
 *
 * <p><strong>料率は申請した時点で決まる。</strong>承認された時点ではない——輸送中に
 * 申請したものは、承認が翌日でも輸送中の料率になる。
 */
public enum CancelledAtStatus {

    /** 仮受付。まだ何も手配していない。 */
    PRELIMINARY(new BigDecimal("0.00")),

    /** 経路提案中。航海の枠は押さえていない。 */
    ROUTE_PROPOSED(new BigDecimal("0.00")),

    /**
     * 荷主へ通知済。
     *
     * <p>経路が決まり、相手先にも伝えた。<strong>手配の連絡が始まっている</strong>ため、
     * わずかに料率が付く。
     */
    ROUTE_NOTIFIED(new BigDecimal("0.05")),

    /** 確定済。航海の枠を押さえている。 */
    CONFIRMED(new BigDecimal("0.10")),

    /** 追跡番号発行済。荷役の手配が動いている。 */
    TRACKING_ISSUED(new BigDecimal("0.10")),

    /**
     * 輸送中。
     *
     * <p><strong>船に載せてから降ろすには実費がかかる。</strong>陸揚げ地を指定して
     * 降ろす作業そのものが発生する（[ADR-025] 決定 4）。
     */
    IN_TRANSIT(new BigDecimal("0.30"));

    private final BigDecimal feeRate;

    CancelledAtStatus(BigDecimal feeRate) {
        this.feeRate = feeRate;
    }

    /** キャンセル料の料率。 */
    public BigDecimal feeRate() {
        return feeRate;
    }

    /**
     * 文字列から引く（ACL 用）。
     *
     * <p><strong>キャンセルできない状態は断る。</strong>既定値に倒すと、
     * 配送完了した貨物にキャンセル料が乗る経路ができる。
     */
    public static CancelledAtStatus of(String name) {
        if (name == null) {
            throw new IllegalArgumentException("キャンセル時の状態を指定してください");
        }
        for (CancelledAtStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "この状態からはキャンセルできません（料率が決まっていません）: " + name);
    }
}
