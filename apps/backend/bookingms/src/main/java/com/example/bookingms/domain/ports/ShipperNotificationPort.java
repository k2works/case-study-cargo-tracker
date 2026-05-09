package com.example.bookingms.domain.ports;

import com.example.bookingms.domain.model.aggregates.Cargo;

/**
 * 荷主通知ポート（ドメイン層インターフェース）
 */
public interface ShipperNotificationPort {

    /**
     * 経路確定を荷主に通知する
     *
     * @param cargo 経路が確定した貨物
     */
    void notifyRouteAssigned(Cargo cargo);
}
