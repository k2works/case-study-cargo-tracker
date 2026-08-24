package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.application.port.CargoCancelled;
import com.example.bookingms.application.port.CargoEventNotifier;
import com.example.bookingms.application.port.TrackingNumberIssued;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 予約のイベントを RabbitMQ へ流す（[ADR-022]）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る。</strong>ドメインもユースケースも
 * {@link CargoEventNotifier} という「何を頼むか」しか知らない
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 */
public class RabbitCargoEventNotifier implements CargoEventNotifier {

    private final RabbitTemplate rabbitTemplate;

    public RabbitCargoEventNotifier(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void trackingNumberIssued(TrackingNumberIssued event) {
        afterCommit(() -> rabbitTemplate.convertAndSend(CargoEventChannels.EXCHANGE,
                CargoEventChannels.TRACKING_NUMBER_ISSUED, event));
    }

    /**
     * キャンセルが確定したことを流す（[ADR-025] 決定 3）。
     *
     * <p><strong>交換機は既存のものに相乗りする。</strong>トピック交換機なので、
     * ルーティングキーを 1 本足すだけで済む。交換機を増やすと、購読側の宣言と
     * 結びつけがそのぶん増える。
     */
    @Override
    public void cargoCancelled(CargoCancelled event) {
        afterCommit(() -> rabbitTemplate.convertAndSend(CargoEventChannels.EXCHANGE,
                CargoEventChannels.CARGO_CANCELLED, event));
    }

    /**
     * コミットしたあとに送る（[ADR-022] 決定 6）。
     *
     * <p>コミット前に出すと、<strong>ロールバックした予約のイベントが飛ぶ</strong>。
     * 存在しない予約の追跡ができ、荷主は追えるのに貨物が無い状態になる。
     *
     * <p><strong>ここで決めるのは、トランザクションの境目がインフラの関心だからである。</strong>
     * ユースケースに「コミット後に呼べ」と作法を課すと、入口が増えた数だけ破られる。
     *
     * <p>トランザクションの外から呼ばれたときはそのまま送る。バッチや管理操作のように
     * トランザクションを張らない経路があり、そこで黙って送られないほうが危ない。
     */
    private void afterCommit(Runnable send) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
}
