package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.ports.CargoEventPublisher;

/**
 * メッセージブローカーが設定されていない環境用の NoOp 実装
 *
 * <p>Bean 登録は {@link MessagingConfiguration} が担当する。
 */
public class NoOpCargoEventPublisher implements CargoEventPublisher {

    @Override
    public void publishCargoRouted(CargoRoutedEvent event) {
        // メッセージブローカー未設定のため、イベントを発行しない
    }
}
