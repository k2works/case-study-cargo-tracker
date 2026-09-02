package com.example.shared.contract;

import java.util.List;

/**
 * 料金試算を問う REST の契約（US01-3・[ADR-028] 決定 6）。
 *
 * <p><strong>向きが逆である。</strong>{@link BillingSnapshotContract} は
 * billingms → bookingms、こちらは bookingms → billingms。本 IT で増えた方向であり、
 * <strong>終盤で新しい結合方式を発明しない</strong>ため既存と同じ形にしている。
 *
 * <p><strong>両側が同じ 1 つを読む。</strong>写しを 2 つ置くと、片方だけ直したことを
 * 誰も検出できない。
 */
public final class ChargeQuoteContract {

    private ChargeQuoteContract() {
    }

    /** 料金を試算する経路。 */
    public static final String PATH = "/api/v1/billing/quotes";

    /**
     * 呼び出してよい主体。
     *
     * <p>名乗らないと、相手の [ADR-007] フィルタが一律に断る。
     */
    public static final String CALLER_PRINCIPAL = "system:bookingms";

    /**
     * 依頼に載る項目。
     *
     * <p><strong>係数は載らない。</strong>式は billingms が持つ——相手が係数を
     * 送れるようにすると、そこが 2 つ目の式になる。
     */
    public static final List<String> REQUEST_FIELDS =
            List.of("legs", "weightKg", "cargoType");

    /** 区間の項目。**地域区分だけを運ぶ**（距離は持っていない）。 */
    public static final List<String> LEG_FIELDS = List.of("loadRegion", "unloadRegion");

    /**
     * 答えに載る項目。
     *
     * <p><strong>基本料金だけ。</strong>割引も税も入れない——見積の時点では荷主が
     * 決まっていないことがあり、契約割引は請求の話である。
     */
    public static final List<String> RESPONSE_FIELDS = List.of("baseAmount");
}
