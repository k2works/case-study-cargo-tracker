package com.example.handlingms.interfaces.events;

import com.example.handlingms.infrastructure.repositories.mybatis.CargoSnapshotMapper;
import com.example.shared.events.CargoTrackedEvent;
import com.example.shared.events.TrackingIssuanceRequestedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CargoSnapshot ACL の投影 EventHandler（IT5 3.1）。
 *
 * <p>bookingms / trackingms が発行する shared モジュールのクロスサービスイベントを購読し、
 * handlingms 専用の {@code cargo_snapshot} Read Model に必要最小情報を保存する。
 * これにより handling 集約 / 投影は bookingms の {@code cargo_summary} を直接参照せずに
 * 業務に必要な情報（出発地・目的地・貨物種別・追跡番号）を解決できる（domain-model.md H5 / ACL）。</p>
 *
 * <p>Kafka tracking モードで購読する。{@link ProcessingGroup} を明示し、
 * {@code KafkaEventProcessingConfig} で {@code StreamableKafkaMessageSource} に束ねる。</p>
 *
 * <p>受信ハンドラは ADR-0011（ホワイトリスト方式）に従わず、ここでは投影への INSERT/UPDATE のみで
 * 集約ロードを伴わないため、例外は基本的に発生しない（冪等 INSERT + UPDATE で再処理にも耐える）。</p>
 */
@Component
@ProcessingGroup("cross-cargo-snapshot")
public class CargoSnapshotProjectionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CargoSnapshotProjectionEventHandler.class);

    private final CargoSnapshotMapper mapper;

    public CargoSnapshotProjectionEventHandler(CargoSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 予約確定時に発行される追跡発行依頼（bookingms BookingSagaManager → trackingms）。
     * 出発地・目的地・貨物種別を {@code cargo_snapshot} に冪等に記録する。
     */
    @EventHandler
    public void on(TrackingIssuanceRequestedEvent event) {
        mapper.upsert(
                event.bookingId(),
                event.originUnlocode(),
                event.destinationUnlocode(),
                event.cargoType()
        );
        log.debug("[cargo-snapshot] upserted booking={} origin={} dest={} type={}",
                event.bookingId(), event.originUnlocode(),
                event.destinationUnlocode(), event.cargoType());
    }

    /**
     * 追跡採番完了通知（trackingms → bookingms）。tracking_number を確定する。
     * snapshot が未存在の場合は WARN ログだけ残してスキップする（再処理時にリプレイ可能）。
     */
    @EventHandler
    public void on(CargoTrackedEvent event) {
        int updated = mapper.updateTrackingNumber(event.bookingId(), event.trackingNumber());
        if (updated == 0) {
            log.warn("[cargo-snapshot] booking={} の snapshot が未到着のため tracking_number={} の更新をスキップ",
                    event.bookingId(), event.trackingNumber());
        }
    }
}
