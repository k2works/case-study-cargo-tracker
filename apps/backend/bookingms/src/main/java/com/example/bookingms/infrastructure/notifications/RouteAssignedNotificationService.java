package com.example.bookingms.infrastructure.notifications;

import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.ports.ShipperNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 荷主通知サービス（stub 実装: ログ出力のみ）
 */
@Service
public class RouteAssignedNotificationService implements ShipperNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(RouteAssignedNotificationService.class);

    @Override
    public void notifyRouteAssigned(Cargo cargo) {
        log.info("[STUB] 経路確定通知: 予約ID={}, 出発地={}, 目的地={}, 到着期限={}",
                cargo.getBookingId().getId(),
                cargo.getRouteSpecification().getOriginUnlocode(),
                cargo.getRouteSpecification().getDestinationUnlocode(),
                cargo.getRouteSpecification().getArrivalDeadline());
    }
}
