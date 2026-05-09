package com.example.trackingms.domain.ports;

import com.example.trackingms.domain.events.TrackingNumberIssuedEvent;

/**
 * 追跡ドメインイベント発行ポート
 *
 * <p>インフラ実装（RabbitMQ 等）に依存せず、ドメイン層からイベントを発行するための抽象。
 */
public interface TrackingEventPublisher {

    /**
     * 追跡番号発行済みイベントを発行する
     *
     * @param event 追跡番号発行イベント
     */
    void publishTrackingNumberIssued(TrackingNumberIssuedEvent event);
}
