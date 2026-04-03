package com.example.cargotracker.billing.domain.model.commands;

/**
 * 法人割引を適用するコマンド。
 *
 * @param freightChargeId 割引を適用する輸送料金 ID（UUID 文字列）
 * @param bookingId       紐づく予約 ID（荷主情報の取得に使用）
 */
public record ApplyDiscountCommand(String freightChargeId, String bookingId) {

    public ApplyDiscountCommand {
        if (freightChargeId == null || freightChargeId.isBlank())
            throw new IllegalArgumentException("輸送料金 ID は必須です");
        if (bookingId == null || bookingId.isBlank())
            throw new IllegalArgumentException("予約 ID は必須です");
    }
}
