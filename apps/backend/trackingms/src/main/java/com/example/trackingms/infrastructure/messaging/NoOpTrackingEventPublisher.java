package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.domain.events.TrackingNumberIssuedEvent;
import com.example.trackingms.domain.ports.TrackingEventPublisher;

/**
 * メッセージブローカーが設定されていない環境用の NoOp 実装
 *
 * <p>Bean 登録は {@link TrackingMessagingConfiguration} が担当する。
 */
public class NoOpTrackingEventPublisher implements TrackingEventPublisher {

    @Override
    public void publishTrackingNumberIssued(TrackingNumberIssuedEvent event) {
        // メッセージブローカー未設定のため、イベントを発行しない
    }
}
