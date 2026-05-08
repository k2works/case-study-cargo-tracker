package com.example.bookingms.domain.ports;

import com.example.bookingms.domain.events.CargoRoutedEvent;

/**
 * 貨物ドメインイベント発行ポート
 *
 * <p>インフラ実装（RabbitMQ 等）に依存せず、ドメイン層からイベントを発行するための抽象。
 */
public interface CargoEventPublisher {

    /**
     * 経路割当完了イベントを発行する
     *
     * @param event 経路割当イベント
     */
    void publishCargoRouted(CargoRoutedEvent event);
}
