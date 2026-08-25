package com.example.handlingms.infrastructure.messaging;

import com.example.handlingms.application.port.CustomsStatusChanged;
import com.example.handlingms.application.port.HandlingActivityRegistered;
import com.example.handlingms.application.port.HandlingEventNotifier;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 荷役のイベントを RabbitMQ へ流す（[ADR-023] 決定 5）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る。</strong>ドメインもユースケースも
 * {@link HandlingEventNotifier} という「何を頼むか」しか知らない。
 */
public class RabbitHandlingEventNotifier implements HandlingEventNotifier {

    private final RabbitTemplate rabbitTemplate;

    public RabbitHandlingEventNotifier(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void handlingActivityRegistered(HandlingActivityRegistered event) {
        afterCommit(() -> rabbitTemplate.convertAndSend(HandlingEventChannels.EXCHANGE,
                HandlingEventChannels.HANDLING_ACTIVITY_REGISTERED, event));
    }

    /**
     * 通関状態が変わったことを流す（US29-5）。
     *
     * <p>交換機は荷役のものに相乗りする。ルーティングキーが違うので、荷役だけを
     * 読んでいる購読者には配られない。
     */
    @Override
    public void customsStatusChanged(CustomsStatusChanged event) {
        afterCommit(() -> rabbitTemplate.convertAndSend(HandlingEventChannels.EXCHANGE,
                HandlingEventChannels.CUSTOMS_STATUS_CHANGED, event));
    }

    /**
     * コミットしたあとに送る（[ADR-022] 決定 6）。
     *
     * <p>コミット前に出すと、<strong>ロールバックした荷役のイベントが飛ぶ</strong>。
     * 記録されていない作業で追跡が進み、荷主は起きていないことを見る。
     *
     * <p><strong>境目をここで決めるのは、それがインフラの関心だからである。</strong>
     * ユースケースに「コミット後に呼べ」と作法を課すと、入口が増えた数だけ破られる
     * （IT6 では境目がリポジトリの save にしか無く、この機構が一度も働いていなかった）。
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
