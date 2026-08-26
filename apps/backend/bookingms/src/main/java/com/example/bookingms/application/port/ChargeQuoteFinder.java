package com.example.bookingms.application.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * 料金の試算を問う出力ポート（US01-3・[ADR-028] 決定 6）。
 *
 * <p><strong>式はこちらに持たない。</strong>見積の概算料金と実料金（US21）は同じ式で
 * なければならない——2 つ持てば必ずずれ、荷主に出した見積と請求が違う金額になる。
 *
 * <p>返すのは Booking Context の型（{@code BigDecimal}）である。billingms の
 * {@code Money} をここへ持ち込むと、相手のドメインの変更がこちらのコンパイルを壊す。
 */
public interface ChargeQuoteFinder {

    /**
     * 経路の基本料金を試算する。
     *
     * @param legs 区間ごとの両端の地域区分
     * @param weightKg 重量
     * @param cargoType 貨物種別
     * @return 基本料金
     */
    BigDecimal quote(List<QuoteLeg> legs, BigDecimal weightKg, String cargoType);

    /**
     * 試算の入力になる 1 区間。
     *
     * @param loadRegion 積み地の地域区分
     * @param unloadRegion 揚げ地の地域区分
     */
    record QuoteLeg(String loadRegion, String unloadRegion) {
    }
}
