package com.example.cargotracker.booking.application.reaction;

import com.example.cargotracker.booking.application.port.ProcessStateService;
import com.example.cargotracker.booking.domain.model.commands.RevertTrackingNumberCommand;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.infrastructure.projection.AttentionItemRecorder;
import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import java.time.Clock;
import java.util.Map;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 予約 → 追跡開始の連鎖（US14 / ADR-0010）。<b>Reaction Handler の 1 本目</b>。
 *
 * <p><b>Saga ではない。</b> Axon 5 に Saga の API が無い（ADR-0001 決定 6）。段の数だけ
 * ハンドラを並べ、途中経過は {@code process_state} に持つ。Saga のストアに直列化して
 * 埋めるのと違い、<b>止まった位置がそのまま SQL で読める</b>。</p>
 *
 * <p><b>投影と別のパッケージに置く。</b> Processing Group はパッケージ名で分ける
 * （{@code @ProcessingGroup} は Axon 5 に無い）。同じにすると、投影のリプレイで
 * コマンドが再送され、追跡が作り直される。</p>
 *
 * <p><b>2 段しかない。</b> 追跡番号の発行そのものは経路設計者の操作なので、連鎖は
 * 発行された<b>あと</b>から始まる（ADR-0010 決定 3）。</p>
 */
@Component
public class BookingReactionHandler {

    /** 連鎖の種類。data-model.md が「現時点の該当はこれだけ」と名指ししている。 */
    public static final String PROCESS_TYPE = "BOOKING_TO_TRACKING";
    /** 1 段目: 追跡開始のコマンドを送った。 */
    public static final String STEP_INITIALIZE_TRACKING = "INITIALIZE_TRACKING";
    /** 2 段目: trackingms が追跡を開始した。 */
    public static final String STEP_TRACKING_INITIALIZED = "TRACKING_INITIALIZED";
    private static final int TOTAL_STEPS = 2;
    /**
     * 追跡開始を送り直す上限（ADR-0010 決定 4）。
     *
     * <p>超えたら補償する。無制限に再試行すると、trackingms が長く落ちているあいだ
     * 誰にも見えないまま溜まり続ける。</p>
     */
    private static final int MAX_ATTEMPTS = 3;
    /** 要確認一覧の種類。連鎖を補償したことを表す。 */
    private static final String COMPENSATED = "CHAIN_COMPENSATED";
    /**
     * 補償の宛先は<b>経路設計者</b>。
     *
     * <p>設計（architecture_backend.md）は「追跡管理者の要確認一覧に写す」と書いていたが、
     * <b>追跡管理者には打つ手が無い</b>——追跡番号を発行し直せるのは経路設計者だけである
     * （ADR-0010 決定 3）。気づく手段は、その人が次に取れる行動へ繋がらなければ意味がない。</p>
     */
    private static final String ROLE_ROUTING = "ROLE_ROUTING";

    private static final Logger log = LoggerFactory.getLogger(BookingReactionHandler.class);

    private final CommandGateway commands;
    private final ProcessStateService processes;
    private final AttentionItemRecorder attentionItems;
    private final Clock clock;

    public BookingReactionHandler(CommandGateway commands, ProcessStateService processes,
            AttentionItemRecorder attentionItems, Clock clock) {
        this.commands = commands;
        this.processes = processes;
        this.attentionItems = attentionItems;
        this.clock = clock;
    }

    /**
     * 1 段目。追跡番号が発行されたら、trackingms へ追跡開始を送る。
     *
     * <p><b>起票してから送る。</b> 送ってから起票すると、trackingms の応答のほうが
     * 先に届いて「行が無いのに 2 段目が来る」ことが起きる。</p>
     *
     * <p><b>値を落とさずに渡す。</b> 旅程は荷役（IT9）の材料になる。ここで落とすと、
     * 契約イベントに載せた意味が無くなる。</p>
     */
    @EventHandler
    public void on(TrackingNumberIssuedEvent event) {
        var state = processes.start(PROCESS_TYPE, event.bookingId(), STEP_INITIALIZE_TRACKING,
                TOTAL_STEPS, Map.of("trackingNumber", event.trackingNumber()));
        if (!state.isRunning()) {
            // 終わった連鎖に遅れて届いた。送り直すと追跡が作り直される。
            return;
        }

        try {
            commands.sendAndWait(new InitializeTrackingCommand(
                    event.trackingNumber(), event.bookingId(),
                    event.origin(), event.destination(), event.cargoType(),
                    event.legs().stream().map(leg -> new InitializeTrackingCommand.LegDto(
                            leg.voyageNumber(), leg.loadUnLocode(), leg.unloadUnLocode(),
                            leg.loadTime(), leg.unloadTime())).toList(),
                    event.issuedAt()));
            // 送れたところまでを 1 段目の完了とする。**送る前に進めない**——届いて
            // いないのに「1 段終わった」と読めると、滞留の走査から漏れる。
            processes.advance(PROCESS_TYPE, event.bookingId(),
                    STEP_INITIALIZE_TRACKING, STEP_TRACKING_INITIALIZED);
        } catch (RuntimeException e) {
            // **握りつぶさない。** 例外を投げ直すと Axon の Event Processor が
            // 再試行する。上限を超えたときだけ補償へ落とす（ADR-0010 決定 4）。
            int attempts = attemptsOf(state) + 1;
            processes.recordAttempt(PROCESS_TYPE, event.bookingId(), attempts);
            if (attempts < MAX_ATTEMPTS) {
                throw e;
            }
            compensate(event, e);
        }
    }

    /**
     * 上限を超えた連鎖を補償する（ADR-0010 決定 4）。
     *
     * <p><b>予約は {@code CONFIRMED} に留まる。</b> キャンセルではないので、追跡番号の
     * 発行だけを取り消し、経路設計者がもう一度発行できるようにする。</p>
     *
     * <p><b>要確認一覧に出す。</b> 補償したことが誰にも見えないと、荷主は追跡番号を
     * 受け取ったのに追跡できない状態のまま放置される。<b>宛先は経路設計者</b>——
     * 発行し直せるのはその人だけだからである。</p>
     */
    private void compensate(TrackingNumberIssuedEvent event, RuntimeException cause) {
        String reason = "追跡の開始が " + MAX_ATTEMPTS + " 回とも届きませんでした";
        log.error("連鎖を補償する: bookingId={} trackingNumber={}",
                event.bookingId(), event.trackingNumber(), cause);
        commands.sendAndWait(new RevertTrackingNumberCommand(event.bookingId(), reason));
        processes.compensate(PROCESS_TYPE, event.bookingId(), reason);
        attentionItems.add(COMPENSATED, "BOOKING", event.bookingId(), ROLE_ROUTING,
                reason, "{}", clock.instant());
    }

    private static int attemptsOf(com.example.cargotracker.booking.application.port.ProcessState
            state) {
        String recorded = state.metadata().get("attempts");
        return recorded == null ? 0 : Integer.parseInt(recorded);
    }

    /**
     * 2 段目。trackingms が追跡を開始したら連鎖を終える（{@code @EndSaga} の代わり）。
     *
     * <p><b>行は消さない。</b> 通り終えたことも記録である。消すと「一度も走らなかった」
     * と区別できない。</p>
     */
    @EventHandler
    public void on(TrackingInitializedEvent event) {
        if (processes.find(PROCESS_TYPE, event.bookingId()).isEmpty()) {
            // 起票されていない連鎖に応答だけが届いた。追跡は始まっているので業務は
            // 進むが、途中経過が追えないことは記録に残す（黙って進めない）。
            log.warn("起票されていない連鎖の応答が届いた: bookingId={}", event.bookingId());
            return;
        }
        var state = processes.advance(PROCESS_TYPE, event.bookingId(),
                STEP_TRACKING_INITIALIZED, STEP_TRACKING_INITIALIZED);
        if (!state.allStepsDone()) {
            // 起票されていない連鎖に応答だけが届いた。追跡は始まっているので
            // 業務は進むが、途中経過が追えないことは記録に残す。
            log.warn("段が進まなかった: bookingId={} currentStep={}",
                    event.bookingId(), state.currentStep());
        }
    }
}
