package com.example.handlingms.interfaces.events;

import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import com.example.handlingms.domain.events.UnexpectedHandlingDetectedEvent;
import com.example.handlingms.domain.projections.CargoSnapshot;
import com.example.handlingms.infrastructure.repositories.mybatis.CargoSnapshotMapper;
import com.example.handlingms.infrastructure.repositories.mybatis.HandlingActivityMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * handling_activity Read Model の投影 EventHandler（US15・US16 / IT5 3.x）。
 *
 * <p>ローカルイベント {@link HandlingActivityRegisteredEvent} を受信し、CargoSnapshot ACL から
 * 必要な属性（bookingId / origin / destination / cargoType）を補って handling_activity テーブルに
 * 1 行追記する。Snapshot が未到着の場合は WARN ログ + Micrometer Counter（IT5 レビュー H2 対応）で
 * 観測可能化したうえでフォールバック値で投影する。フォールバック発生件数は
 * {@code /actuator/metrics/handlingms.projection.snapshot_missing} で確認できる。</p>
 */
@Component
public class HandlingActivityProjectionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(HandlingActivityProjectionEventHandler.class);

    /** メトリクス名: CargoSnapshot 未到着で投影をフォールバック値で進めた回数。 */
    static final String METRIC_SNAPSHOT_MISSING = "handlingms.projection.snapshot_missing";

    private final HandlingActivityMapper handlingActivityMapper;
    private final CargoSnapshotMapper cargoSnapshotMapper;
    private final Counter snapshotMissingCounter;

    public HandlingActivityProjectionEventHandler(HandlingActivityMapper handlingActivityMapper,
                                                  CargoSnapshotMapper cargoSnapshotMapper,
                                                  MeterRegistry meterRegistry) {
        this.handlingActivityMapper = handlingActivityMapper;
        this.cargoSnapshotMapper = cargoSnapshotMapper;
        this.snapshotMissingCounter = Counter.builder(METRIC_SNAPSHOT_MISSING)
                .description("CargoSnapshot 未到着で handling_activity をフォールバック値で投影した回数（cross-service 順序不整合の検知）")
                .register(meterRegistry);
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
            snapshotMissingCounter.increment();
            log.warn("[handling-projection] trackingNumber={} の CargoSnapshot が未到着（累計 {} 件）。"
                            + "ダミー値（UNKNOWN-BOOKING / UNK / UNKNOWN）で投影を進めます",
                    event.trackingNumber(), (long) snapshotMissingCounter.count());
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
