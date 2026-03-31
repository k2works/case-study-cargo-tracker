package com.example.cargotracker.booking.application;

/**
 * 指定した荷主 ID に対応する荷主が存在しない場合にスローされる例外。
 */
public class ShipperNotFoundException extends RuntimeException {

    public ShipperNotFoundException(String shipperId) {
        super("荷主が見つかりません: " + shipperId);
    }
}
