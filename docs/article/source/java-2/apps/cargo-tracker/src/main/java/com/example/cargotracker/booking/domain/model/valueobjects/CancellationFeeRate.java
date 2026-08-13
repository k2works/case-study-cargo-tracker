package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * キャンセル料の料率（US30。ADR-022）。
 *
 * <p><strong>料率は状態で決まる。</strong> 日付ではない。何日前に言ったかではなく、
 * <strong>こちらがどこまで手配を進めていたか</strong>が失う費用を決める。
 *
 * <table>
 *   <caption>料率</caption>
 *   <tr><td>仮予約・経路提案済</td><td>0%</td><td>経路も便も押さえていない</td></tr>
 *   <tr><td>確認済・追跡番号発行済</td><td>20%</td>
 *       <td>便を押さえており、空けた枠は他へ売れない</td></tr>
 *   <tr><td>輸送中</td><td>50%</td>
 *       <td><strong>すでに運んでいる。</strong> 陸揚げと戻しの費用が別途かかる</td></tr>
 * </table>
 *
 * <p><strong>基準は輸送料金の基本料金（割引前・調整前）である。</strong>
 * 割引は「運びきったこと」への取引条件であり、運びきらなかった輸送に適用する理由が無い。
 * 調整は起きた例外への対応であり、キャンセルの料率とは別の話である。
 *
 * <p><strong>金額そのものは Billing が算出する。</strong> ここが持つのは率だけである
 * （金額の正典は Billing にある。Booking から金額を送ると基準額が 2 つ生まれる）。
 */
public record CancellationFeeRate(BigDecimal value) {

    /** 料率が 0 の状態（<strong>キャンセル料は発生しない</strong>）。 */
    public static final CancellationFeeRate NONE =
            new CancellationFeeRate(BigDecimal.ZERO);

    public CancellationFeeRate {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "キャンセル料の料率は 0 以上 1 以下である必要があります: " + value);
        }
    }

    /**
     * 予約の状態から料率を決める。
     *
     * <p><strong>知らない状態を 0% にしない。</strong> 黙って 0 を返すと、
     * 状態を足した日にキャンセル料が消える（{@code CargoTypeFactor} と同じ判断）。
     *
     * @throws IllegalArgumentException キャンセルの対象にならない状態のとき
     */
    public static CancellationFeeRate of(BookingStatus status) {
        return switch (status) {
            case PRELIMINARY, ROUTE_PROPOSED -> NONE;
            case CONFIRMED, TRACKING_ISSUED ->
                    new CancellationFeeRate(new BigDecimal("0.20"));
            case IN_TRANSIT -> new CancellationFeeRate(new BigDecimal("0.50"));
            default -> throw new IllegalArgumentException(
                    "%s の予約はキャンセルできません".formatted(status.displayName()));
        };
    }

    /** キャンセル料が発生するか。<strong>0 円の請求書は送る相手がいない。</strong> */
    public boolean chargeable() {
        return value.signum() > 0;
    }

    /** 画面に出す百分率（{@code 50.00}）。<strong>切り捨てる。</strong> */
    public BigDecimal asPercent() {
        return value.multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.DOWN);
    }
}
