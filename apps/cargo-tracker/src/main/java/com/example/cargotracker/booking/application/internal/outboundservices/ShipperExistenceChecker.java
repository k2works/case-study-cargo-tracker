package com.example.cargotracker.booking.application.internal.outboundservices;

import com.example.cargotracker.shared.domain.model.ShipperId;

/**
 * Shipper コンテキストへの ACL（腐敗防止層）ポート。
 * Booking コンテキストが Shipper の存在確認を行うためのインターフェース。
 */
public interface ShipperExistenceChecker {

    boolean exists(ShipperId shipperId);
}
