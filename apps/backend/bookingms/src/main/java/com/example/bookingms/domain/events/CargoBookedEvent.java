package com.example.bookingms.domain.events;

import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.RouteSpecification;

/**
 * 貨物予約登録完了イベント（US04）。
 *
 * <p>初期状態の bookingStatus = "PRELIMINARY"、routingStatus = "NOT_ROUTED"。
 * Read Model の cargo_summary テーブル更新トリガーとなる。</p>
 */
public record CargoBookedEvent(
        String bookingId,
        String shipperId,
        RouteSpecification routeSpec,
        CargoSpecification cargoSpec,
        String bookingStatus,
        String routingStatus
) {
}
