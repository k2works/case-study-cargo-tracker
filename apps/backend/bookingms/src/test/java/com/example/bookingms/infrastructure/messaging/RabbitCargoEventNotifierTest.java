package com.example.bookingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.bookingms.application.port.TrackingNumberIssued;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * イベントを流すアダプタ（[ADR-022]）。
 *
 * <p>ここで確かめるのは<strong>いつ・どこへ送るか</strong>である。実際に届くことは
 * trackingms 側の往復テスト（実 RabbitMQ）が見る。
 */
@DisplayName("予約のイベントの発行")
class RabbitCargoEventNotifierTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitCargoEventNotifier notifier = new RabbitCargoEventNotifier(rabbitTemplate);

    private static final TrackingNumberIssued EVENT = new TrackingNumberIssued(
            "TRK-20260822-0001", "BKG-2026000001", "JPTYO", "USLAX",
            LocalDate.of(2030, Month.SEPTEMBER, 20), Instant.parse("2026-08-22T02:00:00Z"));

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
        notifier.trackingNumberIssued(EVENT);

        verify(rabbitTemplate).convertAndSend(
                eq(CargoEventChannels.EXCHANGE),
                eq(CargoEventChannels.TRACKING_NUMBER_ISSUED),
                eq((Object) EVENT));
    }

    /**
     * <strong>コミットする前は送らない</strong>（[ADR-022] 決定 6）。
     *
     * <p>コミット前に出すと、ロールバックした予約のイベントが飛ぶ。存在しない予約の追跡が
     * でき、荷主は追えるのに貨物が無い状態になる。
     */
    @Test
    @DisplayName("トランザクションの中では、コミットするまで送らない")
    void waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.trackingNumberIssued(EVENT);

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
        notifier.trackingNumberIssued(EVENT);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        verify(rabbitTemplate).convertAndSend(
                eq(CargoEventChannels.EXCHANGE),
                eq(CargoEventChannels.TRACKING_NUMBER_ISSUED),
                eq((Object) EVENT));
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
        notifier.trackingNumberIssued(EVENT);

        // ロールバックでは afterCommit が呼ばれない
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization
                                .STATUS_ROLLED_BACK));

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class),
                any(Object.class));
    }
}
