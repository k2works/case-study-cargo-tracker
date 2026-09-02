package com.example.bookingms.interfaces.rest;

/**
 * 同じメールアドレスの荷主が既にあることを伝える応答。
 *
 * <p>エラーではなく問いかけである。営業担当者は既存を使うか別の荷主として登録するかを選ぶ。
 */
public record DuplicateShipperResponse(String message, ShipperResponse existing)
        implements ShipperRegistrationResponse {
}
