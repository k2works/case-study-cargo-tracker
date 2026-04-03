package com.example.cargotracker.billing.application.internal.outboundservices;

import java.math.BigDecimal;

/**
 * Billing BC から Shipper BC への ACL クエリポート。
 * 予約に紐づく荷主の割引率を取得する。
 */
public interface ShipperDiscountQueryPort {

    /**
     * 予約 ID から荷主の割引率を取得する。
     * 個人荷主または法人契約情報がない場合は {@link BigDecimal#ZERO} を返す。
     *
     * @param bookingId 予約 ID
     * @return 割引率（0〜30）。割引なしの場合は {@link BigDecimal#ZERO}
     */
    BigDecimal findDiscountRateByBookingId(String bookingId);
}
