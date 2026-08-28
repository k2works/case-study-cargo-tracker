package com.example.handlingms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.handlingms.application.internal.outboundservices.acl.HandlingActivityRegistered;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Instant;
import java.util.List;
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

    private static final com.example.handlingms.application.internal.outboundservices.acl.CustomsStatusChanged
            CUSTOMS_EVENT = new com.example.handlingms.application.internal.outboundservices.acl.CustomsStatusChanged(
                    "TRK-20260823-0001", "BKG-2026000001", "DEC-2026-0001", "PENDING", "HELD",
                    "書類不備のため留置", "tracker1", Instant.parse("2026-08-23T03:00:00Z"),
                    Instant.parse("2026-08-23T03:00:05Z"));

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
     * <p>`CargoDeliveredEvent`（billingms へ）は US23（IT12）である。「出ること」だけを
     * 見ると、余分なイベントが増えても緑のままになる。発行の窓口を、ポートの形から
     * 導いて固定する。
     *
     * <p><strong>数ではなく名前で固定する。</strong>IT7 では「1 つだけ」と数で書いており、
     * US29-5 で通関状態を足したときに赤くなった——赤くなったこと自体は設計どおりだが、
     * 数だけでは「1 本足して 1 本消した」入れ替えを見逃す。何を発行してよいかを
     * 名前で並べる。
     */
    @Test
    @DisplayName("発行してよい種類は、この 2 つだけ")
    void publishesOnlyTheAgreedKindsOfEvent() {
        assertThat(com.example.handlingms.application.internal.outboundservices.acl.HandlingEventNotifier.class
                        .getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .as("発行するイベントが増減した。ADR-023 決定 5 に足すか、増やさないこと")
                .containsExactlyInAnyOrder("handlingActivityRegistered", "customsStatusChanged");
    }

    /**
     * 通関状態のイベントも<strong>コミットするまで送らない</strong>。
     *
     * <p>送出の作法はメソッドごとに書ける。片方だけ守っていても、上の検査（呼び出し箇所の
     * 数）は緑のままになる——留置を記録しそこねた取引で例外だけが起票される。
     */
    @Test
    @DisplayName("通関状態の発行も、コミットするまで送らない")
    void customsStatusWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notifier.customsStatusChanged(CUSTOMS_EVENT);

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class),
                any(Object.class));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(rabbitTemplate).convertAndSend(
                HandlingEventChannels.EXCHANGE,
                HandlingEventChannels.CUSTOMS_STATUS_CHANGED,
                (Object) CUSTOMS_EVENT);
    }

    /**
     * <strong>ポートを通さずに発行する経路も塞ぐ。</strong>
     *
     * <p>上の検査はポートの形だけを見ているため、<strong>ポートに足さずに
     * メッセージ基盤を直接呼べば迂回できる</strong>。`eventPublishingOnlyInMessagingInfrastructureRule`
     * が守るのは「どこで呼ぶか」であって「何本呼ぶか」ではない。2 つの検査を並べても、
     * その隙間は誰も見ていない。
     *
     * <p>そこで<strong>実際の発行の呼び出し箇所を数える</strong>。イベントを 1 本足せば、
     * ポートに足しても足さなくても、ここが赤になる。
     */
    @Test
    @DisplayName("発行の呼び出しは、合意した 2 メソッドだけにある")
    void hasExactlyOnePublishingCallSite() {
        List<String> callers = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.handlingms").stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(RabbitHandlingEventNotifierTest::publishes)
                .map(method -> method.getOwner().getSimpleName() + "#" + method.getName())
                .sorted()
                .toList();

        assertThat(callers)
                .as("発行の呼び出し箇所が増減した。ポートに足さずに直接送る経路も、"
                        + "ADR-023 決定 5 の「発行してよい種類」を破る")
                .containsExactly("RabbitHandlingEventNotifier#customsStatusChanged",
                        "RabbitHandlingEventNotifier#handlingActivityRegistered");
    }

    /** メッセージ基盤へ送り出しているか。型名でも名前でもなく、送信のメソッドで見る。 */
    private static boolean publishes(com.tngtech.archunit.core.domain.JavaMethod method) {
        return method.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().getPackageName()
                                .startsWith("org.springframework.amqp")
                        && call.getName().startsWith("convertAndSend"));
    }
}
