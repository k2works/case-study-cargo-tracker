package com.example.trackingms.interfaces.events;

import com.example.shared.events.CargoTrackedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 追跡採番完了の cross-service publisher（US14 / IT5 タスク 1.4）。
 *
 * <p>{@link TrackingInitializedEvent}（trackingms ローカル）を購読し、shared モジュールの
 * {@link CargoTrackedEvent} を {@link EventGateway} 経由で Kafka に publish する。
 * 受信側 bookingms の {@code BookingSagaManager} は {@code @SagaEventHandler(CargoTrackedEvent)} +
 * {@code @EndSaga} で予約 Saga を終了し、予約状態を TRACKING_ISSUED に更新する。</p>
 *
 * <p>本ハンドラはローカルイベントを購読するため、default プロセッサ（event store source）で動作する。
 * cross-service 受信ハンドラとは異なり、ADR-0011 のホワイトリスト try-catch は不要
 * （EventGateway.publish 失敗時は default プロセッサのエラーハンドラに委ねる）。</p>
 */
@Component
public class CargoTrackedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CargoTrackedEventPublisher.class);

    private final EventGateway eventGateway;

    public CargoTrackedEventPublisher(EventGateway eventGateway) {
        this.eventGateway = eventGateway;
    }

    @EventHandler
    public void on(TrackingInitializedEvent event) {
        log.info("追跡採番完了を bookingms に通知します（bookingId={}, trackingNumber={}）",
                event.bookingId(), event.trackingNumber());
        eventGateway.publish(new CargoTrackedEvent(event.bookingId(), event.trackingNumber()));
    }
}
