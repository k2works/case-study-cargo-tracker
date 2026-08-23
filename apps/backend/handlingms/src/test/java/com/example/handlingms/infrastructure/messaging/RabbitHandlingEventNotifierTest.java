package com.example.handlingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.handlingms.application.port.HandlingActivityRegistered;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * イベントを流すアダプタ（[ADR-023] 決定 5）。
 *
 * <p>ここで確かめるのは<strong>いつ・どこへ送るか</strong>である。実際に届くことは
 * trackingms 側の往復テスト（実 RabbitMQ）が見る。
 */
@DisplayName("荷役のイベントの発行")
class RabbitHandlingEventNotifierTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitHandlingEventNotifier notifier =
            new RabbitHandlingEventNotifier(rabbitTemplate);

    private static final HandlingActivityRegistered EVENT = new HandlingActivityRegistered(
            "TRK-20260823-0001", "BKG-2026000001", "LOAD", "JPTYO",
            Instant.parse("2026-08-23T02:00:00Z"), "V0100", false,
            Instant.parse("2026-08-23T02:05:00Z"));

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * トランザクションの外なら、そのまま送る。
     *
     * <p>バッチや管理操作のようにトランザクションを張らない経路があり、そこで黙って
     * 送られないほうが危ない。
     */
    @Test
    @DisplayName("トランザクションの外では、その場で送る")
    void sendsImmediatelyOutsideATransaction() {
        notifier.handlingActivityRegistered(EVENT);

        verify(rabbitTemplate).convertAndSend(
                HandlingEventChannels.EXCHANGE,
                HandlingEventChannels.HANDLING_ACTIVITY_REGISTERED,
                (Object) EVENT);
    }

    /**
     * <strong>コミットする前は送らない</strong>（[ADR-022] 決定 6）。
     *
     * <p>コミット前に出すと、ロールバックした荷役のイベントが飛ぶ。記録されていない作業で
     * 追跡が進み、荷主は起きていないことを見る。
     */
    @Test
    @DisplayName("トランザクションの中では、コミットするまで送らない")
    void waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.handlingActivityRegistered(EVENT);

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class),
                any(Object.class));
        assertThat(TransactionSynchronizationManager.getSynchronizations())
                .as("コミット後に送る予約をしていない")
                .hasSize(1);
    }

    @Test
    @DisplayName("コミットしたら送る")
    void sendsAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();
        notifier.handlingActivityRegistered(EVENT);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(rabbitTemplate).convertAndSend(
                HandlingEventChannels.EXCHANGE,
                HandlingEventChannels.HANDLING_ACTIVITY_REGISTERED,
                (Object) EVENT);
    }

    /**
     * <strong>ロールバックしたら送らない。</strong>
     *
     * <p>「コミットで送る」だけを確かめると、常に送る実装でも緑になる。
     */
    @Test
    @DisplayName("ロールバックしたら送らない")
    void sendsNothingWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        notifier.handlingActivityRegistered(EVENT);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class),
                any(Object.class));
    }

    /**
     * <strong>発行しないと決めたイベントを発行していない</strong>（[ADR-023] 決定 5）。
     *
     * <p>`CargoDeliveredEvent`（billingms へ）は US26（IT12）である。「出ること」だけを
     * 見ると、余分なイベントが増えても緑のままになる。発行の窓口が 1 つであることを、
     * ポートの形から導いて固定する。
     */
    @Test
    @DisplayName("発行する種類は 1 つだけ")
    void publishesExactlyOneKindOfEvent() {
        assertThat(com.example.handlingms.application.port.HandlingEventNotifier.class
                        .getDeclaredMethods())
                .as("発行するイベントが増えた。ADR-023 決定 5 に足すか、増やさないこと")
                .hasSize(1);
    }
}
