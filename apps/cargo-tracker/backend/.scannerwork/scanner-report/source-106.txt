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
}
