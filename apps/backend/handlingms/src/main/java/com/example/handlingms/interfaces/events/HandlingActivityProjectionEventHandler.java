package com.example.handlingms.interfaces.events;

import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import com.example.handlingms.domain.events.UnexpectedHandlingDetectedEvent;
import com.example.handlingms.domain.projections.CargoSnapshot;
import com.example.handlingms.infrastructure.repositories.mybatis.CargoSnapshotMapper;
import com.example.handlingms.infrastructure.repositories.mybatis.HandlingActivityMapper;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * handling_activity Read Model の投影 EventHandler（US15・US16 / IT5 3.x）。
 *
 * <p>ローカルイベント {@link HandlingActivityRegisteredEvent} を受信し、CargoSnapshot ACL から
 * 必要な属性（bookingId / origin / destination / cargoType）を補って handling_activity テーブルに
 * 1 行追記する。Snapshot が未到着の場合は WARN ログを出して空文字で埋める（再処理ではなく、テストや
 * 開発時のフォールバック用）。</p>
 */
@Component
public class HandlingActivityProjectionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(HandlingActivityProjectionEventHandler.class);

    private final HandlingActivityMapper handlingActivityMapper;
    private final CargoSnapshotMapper cargoSnapshotMapper;

    public HandlingActivityProjectionEventHandler(HandlingActivityMapper handlingActivityMapper,
                                                  CargoSnapshotMapper cargoSnapshotMapper) {
        this.handlingActivityMapper = handlingActivityMapper;
        this.cargoSnapshotMapper = cargoSnapshotMapper;
    }

    /**
     * 予定外検知の警告ログを記録する（IT5 3.2）。
     * 実運用では追跡管理者ダッシュボード等への通知に拡張する。
     */
    @EventHandler
    public void on(UnexpectedHandlingDetectedEvent event) {
        log.warn("[unexpected-handling] activityId={} trackingNumber={} type={} unlocode={} reason={}",
                event.activityId(), event.trackingNumber(), event.handlingType(),
                event.unlocode(), event.reason());
    }

    @EventHandler
    public void on(HandlingActivityRegisteredEvent event) {
        CargoSnapshot snapshot = cargoSnapshotMapper.findByTrackingNumber(event.trackingNumber());
        if (snapshot == null) {
            log.warn("[handling-projection] trackingNumber={} の CargoSnapshot が未到着。"
                    + "ダミー値（UNK / UNK / UNKNOWN）で投影を進めます",
                    event.trackingNumber());
        }
        handlingActivityMapper.insert(
                event.activityId(),
                snapshot != null ? snapshot.getBookingId() : "UNKNOWN-BOOKING",
                event.trackingNumber(),
                snapshot != null ? snapshot.getOriginUnlocode() : "UNK",
                snapshot != null ? snapshot.getDestinationUnlocode() : "UNK",
                snapshot != null ? snapshot.getCargoType() : "UNKNOWN",
                event.handlingType().name(),
                event.occurredAt(),
                event.unlocode(),
                event.voyageNumber(),
                event.handlerId(),
                false
        );
    }
}
