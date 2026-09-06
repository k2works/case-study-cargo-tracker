package com.example.cargotracker.booking.application.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.application.port.ProcessState;
import com.example.cargotracker.booking.application.port.ProcessStateService;
import com.example.cargotracker.booking.domain.model.commands.RevertTrackingNumberCommand;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.infrastructure.projection.AttentionItemRecorder;
import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.commandhandling.gateway.CommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 予約 → 追跡開始の連鎖（US14 / ADR-0010 決定 4）。
 *
 * <p><b>スタブは受け取った引数を捨てない。</b> 送ったコマンドを捕まえてアサートする。
 * 捨てると、組み立てを潰しても緑のままになる（IT6 の実測欠陥）。</p>
 */
class BookingReactionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-08T02:00:00Z");
    private static final Instant ISSUED = Instant.parse("2026-09-08T01:00:00Z");

    /** 送られたコマンドを捕まえるスタブ。失敗させたいときは {@code failure} を立てる。 */
    private static final class RecordingCommands {
        private final List<Object> sent = new ArrayList<>();
        private RuntimeException failure;
    }

    /** 途中経過のフェイク。実装（MyBatis）ではなく振る舞いを写す。 */
    private static final class FakeProcesses implements ProcessStateService {
        private final Map<String, ProcessState> states = new LinkedHashMap<>();

        private static String key(String type, String id) {
            return type + "/" + id;
        }

        @Override
        public ProcessState start(String type, String id, String firstStep, int totalSteps,
                Map<String, String> metadata) {
            return states.computeIfAbsent(key(type, id), k -> new ProcessState(type, id,
                    firstStep, totalSteps, 0, ProcessState.Status.RUNNING, metadata,
                    NOW, NOW, null));
        }

        @Override
        public Optional<ProcessState> find(String type, String id) {
            return Optional.ofNullable(states.get(key(type, id)));
        }

        @Override
        public ProcessState advance(String type, String id, String completedStep,
                String nextStep) {
            ProcessState current = states.get(key(type, id));
            ProcessState next = new ProcessState(type, id, nextStep, current.totalSteps(),
                    current.completedSteps() + 1,
                    current.completedSteps() + 1 >= current.totalSteps()
                            ? ProcessState.Status.COMPLETED : ProcessState.Status.RUNNING,
                    current.metadata(), current.startedAt(), NOW, NOW);
            states.put(key(type, id), next);
            return next;
        }

        @Override
        public ProcessState recordAttempt(String type, String id, int attempts) {
            ProcessState current = states.get(key(type, id));
            Map<String, String> metadata = new LinkedHashMap<>(current.metadata());
            metadata.put("attempts", String.valueOf(attempts));
            ProcessState next = new ProcessState(type, id, current.currentStep(),
                    current.totalSteps(), current.completedSteps(), current.status(),
                    metadata, current.startedAt(), NOW, current.completedAt());
            states.put(key(type, id), next);
            return next;
        }

        @Override
        public ProcessState compensate(String type, String id, String reason) {
            ProcessState current = states.get(key(type, id));
            ProcessState next = new ProcessState(type, id, current.currentStep(),
                    current.totalSteps(), current.completedSteps(),
                    ProcessState.Status.COMPENSATED, current.metadata(),
                    current.startedAt(), NOW, NOW);
            states.put(key(type, id), next);
            return next;
        }

        @Override
        public List<ProcessState> findStuck(String type, Duration olderThan) {
            return List.of();
        }
    }

    /** 要確認一覧への記録を捕まえる。 */
    private static final class RecordingAttentionItems extends AttentionItemRecorder {
        private final List<String> reasons = new ArrayList<>();

        RecordingAttentionItems() {
            super(null);
        }

        @Override
        public void add(String kind, String targetType, String targetId, String assignedRole,
                String reason, String payloadJson, Instant occurredAt) {
            reasons.add(kind + "/" + assignedRole + "/" + reason);
        }
    }

    private RecordingCommands commands;
    private FakeProcesses processes;
    private RecordingAttentionItems attentionItems;
    private BookingReactionHandler handler;

    @BeforeEach
    void setUp() {
        commands = new RecordingCommands();
        processes = new FakeProcesses();
        attentionItems = new RecordingAttentionItems();
        handler = new BookingReactionHandler(gateway(), processes, attentionItems,
                Clock.fixed(NOW, ZoneId.of("Asia/Tokyo")));
    }

    /**
     * 送り口のスタブ。<b>受け取ったコマンドを捨てない。</b>
     *
     * <p>捨てると、組み立てを潰しても検査が緑のままになる（IT6 の実測欠陥）。</p>
     */
    private CommandGateway gateway() {
        return new CommandGateway() {
            @Override
            public CommandResult send(Object command,
                    org.axonframework.messaging.core.Metadata metadata,
                    org.axonframework.messaging.core.unitofwork.ProcessingContext context) {
                commands.sent.add(command);
                if (commands.failure != null && command instanceof InitializeTrackingCommand) {
                    throw commands.failure;
                }
                return () -> CompletableFuture.completedFuture(null);
            }

            @Override
            public void describeTo(
                    org.axonframework.common.infra.ComponentDescriptor descriptor) {
                // 検査には要らない。
            }
        };
    }

    private static TrackingNumberIssuedEvent issued() {
        return new TrackingNumberIssuedEvent("b-1", "T-2026-0001", "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingNumberIssuedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                "routing01", ISSUED);
    }

    @Test
    @DisplayName("1 段目: 追跡番号が発行されたら trackingms へ値を落とさず送る")
    void sendsInitializeTracking() {
        handler.on(issued());

        assertThat(commands.sent).hasSize(1);
        InitializeTrackingCommand sent = (InitializeTrackingCommand) commands.sent.get(0);
        assertThat(sent.trackingNumber()).isEqualTo("T-2026-0001");
        assertThat(sent.bookingId()).isEqualTo("b-1");
        assertThat(sent.originUnLocode()).isEqualTo("JPTYO");
        assertThat(sent.destinationUnLocode()).isEqualTo("USNYC");
        assertThat(sent.cargoType()).isEqualTo("GENERAL");
        // **旅程を落とさない。** 荷役（IT9）の材料になる。
        assertThat(sent.legs()).extracting(InitializeTrackingCommand.LegDto::voyageNumber)
                .containsExactly("V-MOL-001");
    }

    @Test
    @DisplayName("1 段目: コマンドを送る前に起票する（応答が先に届いても行がある）")
    void startsTheProcessBeforeSending() {
        // **送る前に起票する。** 送ってから起票すると、trackingms の応答のほうが
        // 先に届いて「行が無いのに 2 段目が来る」ことが起きる。送れなかったときも
        // 行は残り、滞留の走査に出る。
        commands.failure = new IllegalStateException("trackingms が落ちている");

        assertThatThrownBy(() -> handler.on(issued())).isInstanceOf(RuntimeException.class);

        assertThat(processes.find(BookingReactionHandler.PROCESS_TYPE, "b-1"))
                .get()
                .satisfies(state -> {
                    assertThat(state.isRunning()).isTrue();
                    assertThat(state.currentStep())
                            .as("届いていないのに次の段へ進めない（滞留の走査から漏れる）")
                            .isEqualTo(BookingReactionHandler.STEP_INITIALIZE_TRACKING);
                });
    }

    @Test
    @DisplayName("2 段目: 追跡が始まったら連鎖を終える（行は消さない）")
    void completesTheProcess() {
        handler.on(issued());

        handler.on(new TrackingInitializedEvent("T-2026-0001", "b-1", "JPTYO", "USNYC",
                "GENERAL", List.of(), NOW));

        assertThat(processes.find(BookingReactionHandler.PROCESS_TYPE, "b-1"))
                .get()
                .satisfies(state -> {
                    assertThat(state.status()).isEqualTo(ProcessState.Status.COMPLETED);
                    assertThat(state.allStepsDone()).isTrue();
                });
    }

    @Test
    @DisplayName("ADR-0010 決定 4: 上限までは投げ直す（Event Processor が再試行する）")
    void rethrowsUntilTheLimit() {
        commands.failure = new IllegalStateException("trackingms が落ちている");

        assertThatThrownBy(() -> handler.on(issued()))
                .as("握りつぶすと、届かなかったことが誰にも見えないまま業務が進む")
                .isInstanceOf(IllegalStateException.class);
        assertThat(attentionItems.reasons).isEmpty();
    }

    @Test
    @DisplayName("ADR-0010 決定 4: 上限を超えたら補償して要確認一覧に出す")
    void compensatesAfterTheLimit() {
        commands.failure = new IllegalStateException("trackingms が落ちている");

        // 1 回目・2 回目は投げ直す（再試行される）。
        assertThatThrownBy(() -> handler.on(issued())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> handler.on(issued())).isInstanceOf(RuntimeException.class);
        // 3 回目で上限。投げずに補償する。
        handler.on(issued());

        assertThat(commands.sent)
                .as("予約は CONFIRMED に戻す。キャンセルではない")
                .anyMatch(RevertTrackingNumberCommand.class::isInstance);
        assertThat(processes.find(BookingReactionHandler.PROCESS_TYPE, "b-1"))
                .get()
                .satisfies(state -> assertThat(state.status())
                        .isEqualTo(ProcessState.Status.COMPENSATED));
        assertThat(attentionItems.reasons)
                .as("補償したことが誰にも見えないと、荷主は追跡できないまま放置される")
                .anyMatch(r -> r.startsWith("SAGA_COMPENSATED/ROLE_TRACKER/"));
    }

    @Test
    @DisplayName("起票されていない連鎖の応答は黙って進めない")
    void ignoresUnknownProcess() {
        handler.on(new TrackingInitializedEvent("T-9", "b-unknown", "JPTYO", "USNYC",
                "GENERAL", List.of(), NOW));

        assertThat(processes.find(BookingReactionHandler.PROCESS_TYPE, "b-unknown")).isEmpty();
    }

    @Test
    @DisplayName("終わった連鎖に遅れて届いたイベントは送り直さない（追跡が作り直される）")
    void doesNotResendForCompletedProcess() {
        handler.on(issued());
        handler.on(new TrackingInitializedEvent("T-2026-0001", "b-1", "JPTYO", "USNYC",
                "GENERAL", List.of(), NOW));
        int sentSoFar = commands.sent.size();

        handler.on(issued());

        assertThat(commands.sent)
                .as("終わった連鎖に送り直すと、trackingms に追跡がもう 1 つできる")
                .hasSize(sentSoFar);
    }

    @Test
    @DisplayName("段が進まなかった応答は黙って完了にしない")
    void doesNotCompleteWhenStepDidNotAdvance() {
        // 1 段目を飛ばして 2 段目だけが届いた形。行はあるが段が合わない。
        processes.start(BookingReactionHandler.PROCESS_TYPE, "b-2",
                BookingReactionHandler.STEP_INITIALIZE_TRACKING, 3, Map.of());

        handler.on(new TrackingInitializedEvent("T-2", "b-2", "JPTYO", "USNYC",
                "GENERAL", List.of(), NOW));

        assertThat(processes.find(BookingReactionHandler.PROCESS_TYPE, "b-2"))
                .get()
                .satisfies(state -> assertThat(state.allStepsDone())
                        .as("段が残っているのに完了にすると、抜けに気づけない")
                        .isFalse());
    }
}
