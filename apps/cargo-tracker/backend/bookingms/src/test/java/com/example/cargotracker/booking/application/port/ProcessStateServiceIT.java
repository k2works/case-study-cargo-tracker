package com.example.cargotracker.booking.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.application.port.ProcessState.Status;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 連鎖の途中経過（ADR-0001 決定 6 / data-model.md）。
 *
 * <p>Saga のインフラが隠していた関連付け・終了・タイムアウトを自分で持つので、
 * 「イベントが再配信されても段が飛ばない」ことを自分で確かめる必要がある。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProcessStateServiceIT extends AbstractAxonIntegrationTest {

    private static final String TYPE = "BOOKING_TO_TRACKING";

    @Autowired
    private ProcessStateService service;

    /** 外側のトランザクションを自分で張って、巻き戻りを再現する。 */
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate template;

    private String newId() {
        return "bk-" + System.nanoTime();
    }

    @Test
    @DisplayName("連鎖を始めると実行中の行ができる")
    void starts() {
        String id = newId();

        ProcessState started = service.start(TYPE, id, "CONFIRMED", 3,
                Map.of("origin", "JPTYO", "destination", "USNYC"));

        assertThat(started.status()).isEqualTo(Status.RUNNING);
        assertThat(started.currentStep()).isEqualTo("CONFIRMED");
        assertThat(started.completedSteps()).isZero();
        assertThat(started.totalSteps()).isEqualTo(3);
        assertThat(started.metadata()).containsEntry("origin", "JPTYO");
        assertThat(started.completedAt()).isNull();
    }

    @Test
    @DisplayName("同じ連鎖を 2 度始めても巻き戻らない（イベントは再配信されうる）")
    void startIsIdempotent() {
        String id = newId();
        service.start(TYPE, id, "CONFIRMED", 3, Map.of());
        service.advance(TYPE, id, "CONFIRMED", "TRACKING_NUMBER_ISSUED");

        ProcessState again = service.start(TYPE, id, "CONFIRMED", 3, Map.of());

        assertThat(again.completedSteps())
                .as("作り直すと進んだ段が巻き戻る")
                .isEqualTo(1);
        assertThat(again.currentStep()).isEqualTo("TRACKING_NUMBER_ISSUED");
    }

    @Test
    @DisplayName("段を順に進めると、最後の段で完了する")
    void advancesThroughAllSteps() {
        String id = newId();
        service.start(TYPE, id, "CONFIRMED", 3, Map.of());

        service.advance(TYPE, id, "CONFIRMED", "TRACKING_NUMBER_ISSUED");
        service.advance(TYPE, id, "TRACKING_NUMBER_ISSUED", "TRACKING_INITIALIZED");
        ProcessState finished = service.advance(TYPE, id, "TRACKING_INITIALIZED", null);

        assertThat(finished.status()).isEqualTo(Status.COMPLETED);
        assertThat(finished.allStepsDone()).isTrue();
        assertThat(finished.completedAt())
                .as("完了しても行は消さない。消すと「いつ終わったか」を後から問えない")
                .isNotNull();
    }

    @Test
    @DisplayName("同じ段を 2 度受け取っても進めない（段が飛ぶのを防ぐ）")
    void advanceIsIdempotent() {
        String id = newId();
        service.start(TYPE, id, "CONFIRMED", 3, Map.of());

        service.advance(TYPE, id, "CONFIRMED", "TRACKING_NUMBER_ISSUED");
        ProcessState again = service.advance(TYPE, id, "CONFIRMED", "TRACKING_NUMBER_ISSUED");

        assertThat(again.completedSteps()).isEqualTo(1);
        assertThat(again.currentStep()).isEqualTo("TRACKING_NUMBER_ISSUED");
    }

    @Test
    @DisplayName("終わった連鎖に遅れて届いたイベントで完了が取り消されない")
    void doesNotReopenFinishedProcess() {
        String id = newId();
        service.start(TYPE, id, "ONLY", 1, Map.of());
        service.advance(TYPE, id, "ONLY", null);

        ProcessState late = service.advance(TYPE, id, "ONLY", null);

        assertThat(late.status()).isEqualTo(Status.COMPLETED);
        assertThat(late.completedSteps()).isEqualTo(1);
    }

    @Test
    @DisplayName("補償に至ったことと理由が残る")
    void compensates() {
        String id = newId();
        service.start(TYPE, id, "CONFIRMED", 3, Map.of("bookingId", id));

        ProcessState compensated = service.compensate(TYPE, id, "追跡の初期化が届かなかった");

        assertThat(compensated.status()).isEqualTo(Status.COMPENSATED);
        assertThat(compensated.metadata())
                .containsEntry("compensationReason", "追跡の初期化が届かなかった")
                .as("元の metadata も残す").containsEntry("bookingId", id);
        assertThat(compensated.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("始まっていない連鎖は進められない（黙って作らない）")
    void refusesToAdvanceUnknownProcess() {
        assertThatThrownBy(() -> service.advance(TYPE, newId(), "CONFIRMED", "NEXT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("連鎖が見つかりません");
    }

    @Test
    @DisplayName("実行中のまま古くなった連鎖を滞留として拾う")
    void findsStuckProcesses() {
        String stuck = newId();
        service.start(TYPE, stuck, "CONFIRMED", 3, Map.of());

        // 0 秒より古いものを探せば、いま作った実行中の行が入る。
        assertThat(service.findStuck(TYPE, Duration.ZERO))
                .extracting(ProcessState::processId)
                .contains(stuck);
    }

    @Test
    @DisplayName("完了した連鎖は滞留に出ない")
    void completedProcessIsNotStuck() {
        String done = newId();
        service.start(TYPE, done, "ONLY", 1, Map.of());
        service.advance(TYPE, done, "ONLY", null);

        assertThat(service.findStuck(TYPE, Duration.ZERO))
                .extracting(ProcessState::processId)
                .doesNotContain(done);
    }

    @Test
    @DisplayName("まだ新しい連鎖は滞留に出ない")
    void freshProcessIsNotStuck() {
        String fresh = newId();
        service.start(TYPE, fresh, "CONFIRMED", 3, Map.of());

        assertThat(service.findStuck(TYPE, Duration.ofHours(24)))
                .extracting(ProcessState::processId)
                .doesNotContain(fresh);
    }

    @Test
    @DisplayName("外側のトランザクションが巻き戻っても、起票は残る（IT8 H.1 でクラスタ実測）")
    void startSurvivesOuterRollback() {
        // **連鎖の 1 段目は「起票してからコマンドを送る」。** 送信が失敗すると
        // Reaction Handler は例外を投げ直して Event Processor に再試行させるが、
        // **同じトランザクションだと起票も一緒に巻き戻る**。すると
        // `process_state` に行が残らず、**止まった連鎖が滞留の走査に出ない**。
        //
        // クラスタで trackingms を落として実測した（NoHandlerForCommandException が
        // 出て、行が 1 つも作られなかった）。フェイクを使う単体テストでは
        // トランザクションの巻き戻りを再現しないので判別できなかった。
        String processId = newId();

        assertThatThrownBy(() -> template.execute(status -> {
            service.start(TYPE, processId, "INITIALIZE_TRACKING", 2, Map.of());
            throw new IllegalStateException("送信に失敗した");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(service.find(TYPE, processId))
                .as("起票が巻き戻ると、止まった連鎖が誰にも見えない")
                .isPresent();
    }

    @Test
    @DisplayName("外側が巻き戻っても、再試行の回数は残る（残らないと上限に到達しない）")
    void recordAttemptSurvivesOuterRollback() {
        // 回数が巻き戻ると**永久に 1 のまま**で、上限を超えず補償に落ちない。
        // trackingms が落ちている間、無限に再試行し続けることになる。
        String processId = newId();
        service.start(TYPE, processId, "INITIALIZE_TRACKING", 2, Map.of());

        assertThatThrownBy(() -> template.execute(status -> {
            service.recordAttempt(TYPE, processId, 1);
            throw new IllegalStateException("送信に失敗した");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(service.find(TYPE, processId))
                .get()
                .satisfies(state -> assertThat(state.metadata().get("attempts"))
                        .as("回数が残らないと、上限という概念が成り立たない")
                        .isEqualTo("1"));
    }

    @Test
    @DisplayName("補償した連鎖は、もう一度始められる（IT8 H.1 でクラスタ実測）")
    void compensatedProcessCanBeRestarted() {
        // **補償は行き止まりではない。** 要確認一覧は経路設計者に「追跡番号を
        // 発行し直せ」と言う（ADR-0010 決定 4）。発行し直すと新しい
        // TrackingNumberIssuedEvent が出るので、連鎖もやり直せなければならない。
        //
        // クラスタで実測した——補償のあと再発行すると予約は TRACKING_ISSUED に
        // なるのに、`start` が COMPENSATED の行をそのまま返すので調整役が
        // 何もせず戻り、**trackingms に追跡が作られないまま**になった。
        // 気づく手段が次の行動に繋がらない。
        String processId = newId();
        service.start(TYPE, processId, "INITIALIZE_TRACKING", 2, Map.of());
        service.recordAttempt(TYPE, processId, 3);
        service.compensate(TYPE, processId, "届きませんでした");

        ProcessState restarted =
                service.start(TYPE, processId, "INITIALIZE_TRACKING", 2,
                        Map.of("trackingNumber", "T-2"));

        assertThat(restarted.isRunning())
                .as("補償した連鎖をやり直せないと、発行し直しても追跡が作られない")
                .isTrue();
        assertThat(restarted.completedSteps()).isZero();
        assertThat(restarted.metadata().get("attempts"))
                .as("前回の回数が残ると、1 回の失敗で即座に補償に落ちる")
                .isNull();
        assertThat(restarted.metadata())
                .as("やり直したことは記録に残す（何度も落ちている予約を見つけられる）")
                .containsKey("restartedAfterCompensation");
    }

    @Test
    @DisplayName("実行中・完了した連鎖は始め直さない（再配送で巻き戻らない）")
    void runningOrCompletedProcessIsNotRestarted() {
        String processId = newId();
        service.start(TYPE, processId, "INITIALIZE_TRACKING", 2, Map.of());
        service.advance(TYPE, processId, "INITIALIZE_TRACKING", "TRACKING_INITIALIZED");
        service.advance(TYPE, processId, "TRACKING_INITIALIZED", "TRACKING_INITIALIZED");

        ProcessState again = service.start(TYPE, processId, "INITIALIZE_TRACKING", 2, Map.of());

        assertThat(again.status())
                .as("イベントは再配送されうる。作り直すと進んだ段が巻き戻る")
                .isEqualTo(ProcessState.Status.COMPLETED);
    }
}
