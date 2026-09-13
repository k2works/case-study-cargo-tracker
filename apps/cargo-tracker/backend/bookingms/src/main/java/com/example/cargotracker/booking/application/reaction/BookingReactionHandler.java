package com.example.cargotracker.booking.application.reaction;

import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import com.example.cargotracker.booking.application.port.ProcessStateService;
import com.example.cargotracker.booking.domain.model.commands.RevertTrackingNumberCommand;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.infrastructure.projection.AttentionItemRecorder;
import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.booking.domain.model.commands.RecordHandlingCommand;
import com.example.cargotracker.booking.domain.model.commands.RevertHandlingCommand;
import com.example.cargotracker.booking.domain.model.commands.MarkDeliveredCommand;
import com.example.cargotracker.booking.domain.model.commands.RevertDeliveryCommand;
import com.example.cargotracker.booking.domain.model.commands.RevertSettlementCommand;
import com.example.cargotracker.booking.domain.model.commands.SettleBookingCommand;
import com.example.cargotracker.shared.contract.event.CargoDeliveredEvent;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.shared.contract.event.CargoDeliveryRevertedEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.shared.contract.event.PaymentRecordedEvent;
import com.example.cargotracker.shared.contract.event.PaymentVoidedEvent;
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
 *
 * <p><b>処理の列を予約ごとに分ける。</b> 既定では列が全体で 1 本なので、1 件の毒で
 * <b>無関係の予約のイベントまで退避される</b>（IT12 のクラスタ E2E で 4 件のうち 3 件が
 * 巻き添え）。退避先は順序を守るために「同じ列の後続」も退避するので、列の切り方が
 * そのまま被害の範囲になる。同じ予約の中では順序が要る（訂正は登録より後に効かなければ
 * ならない）ので、予約より細かくは切らない。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "bookingId")
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

    /** 精算の連鎖が進めなかった。**直せるのは請求の側**なので経理宛に出す。 */
    private static final String SETTLEMENT_BLOCKED = "SETTLEMENT_BLOCKED";

    /**
     * 連鎖のコマンドを集約が断った（IT13 引き継ぎ I）。
     *
     * <p><b>退避に積むだけにしない。</b> 集約の断りは再試行しても結果が
     * 変わらないので、業務の担当者が見て決める。</p>
     */
    private static final String CHAIN_REFUSED = "CHAIN_REFUSED";

    private static final String ROLE_ACCOUNTANT = "ROLE_ACCOUNTANT";

    /**
     * 予約の側で断られたものの宛先は<b>営業</b>。
     *
     * <p>予約の状態を動かせるのは営業で、荷役や追跡には打つ手が無い——
     * 気づく手段は、その人が次に取れる行動へ繋がらなければ意味がない。</p>
     */
    private static final String ROLE_SALES = "ROLE_SALES";

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
                    event.trackingNumber(), event.bookingId(), event.shipperId(),
                    event.origin(), event.destination(), event.cargoType(), event.weightKg(),
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

    /**
     * 荷役が記録された（US15・US28 / 不変条件 12）。
     *
     * <p><b>この連鎖は 1 段で終わる。</b> 予約 → 追跡開始（ADR-0010）と違い、
     * 応答を待って次へ進む段がないので {@code process_state} は要らない。
     * 止まったかどうかは、荷役の記録と予約の状態を突き合わせれば読める。</p>
     */
    @EventHandler
    public void on(HandlingActivityRegisteredEvent event) {
        // **断られたら要確認へ**（IT13 引き継ぎ I）。荷役の記録は現場が直せる。
        sendOrRaise(new RecordHandlingCommand(event.bookingId(), event.activityId(),
                event.handlingType(), event.unLocode(), event.offRoute(),
                event.completedAt()), CHAIN_REFUSED, event.bookingId(), ROLE_SALES,
                "荷役 " + event.activityId() + " を予約に写せなかった");
    }

    /**
     * 貨物が引き渡された（UC14 / US16 §受入基準 4）。
     *
     * <p><b>購読側は billingms だけではない。</b> 予約の状態も引取済へ進む
     * （domain-model.md:573）。ここで止めると、営業の一覧は輸送中のまま残る。</p>
     */
    @EventHandler
    public void on(CargoDeliveredEvent event) {
        sendOrRaise(new MarkDeliveredCommand(event.bookingId(),
                event.trackingNumber(), event.deliveredAt(), event.location()),
                CHAIN_REFUSED, event.bookingId(), ROLE_SALES,
                "引き渡しを予約に写せなかった");
    }

    /**
     * 入金が記録された（UC18 / US23 §受入基準 4）。
     *
     * <p><b>精算の輪が閉じる。</b> 入金を知っているのは billingms で、予約は
     * その事実を写して「精算済」になる。{@code BookingStatus.SETTLED} は IT1 から
     * 列挙にあったが、<b>遷移させる相手がここで初めてできる</b>。</p>
     *
     * <p><b>誰が精算したかは入金を記録した人を写す。</b> 連鎖は利用者名を持たない
     * が、「system」で埋めると誰が入金を確かめたのかが予約の側から追えなくなる。</p>
     *
     * <p><b>進めない状態でも黙って退避しない。</b> 引取の記録が取り消されたあとに
     * 入金が届くと、集約は {@code IllegalTransition} で断る——そのまま投げ返すと
     * イベントは退避され、<b>入金は記録されているのに予約だけが精算されないまま</b>
     * 誰の目にも触れない。入金は billingms に残っているので、ここでやり直しても
     * 結果は変わらない。<b>人が見て決める</b>ことなので要確認に出す。</p>
     */
    @EventHandler
    public void on(PaymentRecordedEvent event) {
        try {
            commands.sendAndWait(new SettleBookingCommand(event.bookingId(), event.invoiceId(),
                    event.amount(), event.currency(), event.paidAt(), event.recordedBy()),
                    Void.class);
        } catch (RuntimeException e) {
            IllegalTransition refusal = refusalIn(e);
            if (refusal == null) {
                // 一時的な障害は投げ直す。Event Processor が再試行する——
                // 要確認に落とすと、繋がり直せば済むものを人が見ることになる。
                throw e;
            }
            // **経理宛に出す。** 直せるのは請求の側（入金の取り違えか、引取の
            // 記録の取り消し）で、経路設計者には打つ手が無い。
            String reason = "入金は記録されたが予約を精算済にできなかった（"
                    + refusal.getMessage() + "）。請求書 " + event.invoiceId() + " を確かめる";
            log.warn("精算の連鎖が進めなかった: bookingId={} invoiceId={} reason={}",
                    event.bookingId(), event.invoiceId(), refusal.getMessage());
            attentionItems.add(SETTLEMENT_BLOCKED, "BOOKING", event.bookingId(),
                    ROLE_ACCOUNTANT, reason, "{}", clock.instant());
        }
    }

    /**
     * 入金の記録が取り消された（UC18 / US23。IT15 引き継ぎ 3）。
     *
     * <p><b>記録と打ち消しは同じ経路を通す。</b> 入金を写して精算済にしたのなら、
     * 取り消しも写して引取済に戻す——戻さないと、入金が無いのに精算が終わって
     * いる予約が残る。</p>
     *
     * <p><b>断られたら要確認に出す。</b> 精算済でない予約なら集約は黙って
     * 何もしないが、それ以外の理由で進めないときは経理が見て決める
     * （入金の記録と同じ扱い）。</p>
     */
    @EventHandler
    public void on(PaymentVoidedEvent event) {
        try {
            commands.sendAndWait(new RevertSettlementCommand(event.bookingId(),
                    event.invoiceId(), event.reason()), Void.class);
        } catch (RuntimeException e) {
            IllegalTransition refusal = refusalIn(e);
            if (refusal == null) {
                throw e;
            }
            String reason = "入金は取り消されたが予約の精算を戻せなかった（"
                    + refusal.getMessage() + "）。請求書 " + event.invoiceId() + " を確かめる";
            log.warn("精算の取り消しが進めなかった: bookingId={} invoiceId={} reason={}",
                    event.bookingId(), event.invoiceId(), refusal.getMessage());
            attentionItems.add(SETTLEMENT_BLOCKED, "BOOKING", event.bookingId(),
                    ROLE_ACCOUNTANT, reason, "{}", clock.instant());
        }
    }

    /**
     * コマンドを送り、<b>集約が断ったら要確認へ出す</b>（IT13 引き継ぎ I）。
     *
     * <p><b>断りと障害を分ける。</b> 一時的な障害は投げ直して Event Processor に
     * 再試行させる——繋がり直せば済むものを人が見ることになるからである。
     * <b>集約の断りは再試行しても結果が変わらない</b>ので、退避先に積んでも
     * 誰も気づかないまま溜まる（気づく手段は S91 に置いたが、<b>気づいたあとに
     * 打つ手があるのは業務の担当者</b>である）。</p>
     *
     * <p><b>宛先は「直せる人」。</b> 全員に見えるものは誰も直さない。</p>
     */
    private void sendOrRaise(Object command, String kind, String bookingId,
            String role, String what) {
        try {
            commands.sendAndWait(command, Void.class);
        } catch (RuntimeException e) {
            IllegalTransition refusal = refusalIn(e);
            if (refusal == null) {
                throw e;
            }
            String reason = what + "（" + refusal.getMessage() + "）";
            log.warn("連鎖が進めなかった: kind={} bookingId={} reason={}",
                    kind, bookingId, refusal.getMessage());
            attentionItems.add(kind, "BOOKING", bookingId, role, reason, "{}", clock.instant());
        }
    }

    /**
     * 集約が断ったのかどうか。
     *
     * <p><b>包まれて届く。</b> コマンドバス越しの例外は {@code
     * CommandExecutionException} に包まれるので、素の型では捕まらない。
     * <b>断りと障害を取り違えない</b>ために、原因の連鎖をたどって見分ける。</p>
     */
    private static IllegalTransition refusalIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalTransition refusal) {
                return refusal;
            }
            if (cause.getCause() == cause) {
                return null;
            }
        }
        return null;
    }

    /**
     * 引き渡しの記録が取り消された（IT11 引き継ぎ枠 A）。
     *
     * <p><b>{@code CargoDeliveredEvent} と対で購読する。</b> 片方だけを受けると、
     * 予約が引取済のまま残って追跡だけが巻き戻る。</p>
     */
    @EventHandler
    public void on(CargoDeliveryRevertedEvent event) {
        commands.sendAndWait(new RevertDeliveryCommand(event.bookingId(),
                event.trackingNumber(), event.reason()), Void.class);
    }

    /** 荷役が取り消された（不変条件 13）。 */
    @EventHandler
    public void on(HandlingActivityVoidedEvent event) {
        commands.sendAndWait(new RevertHandlingCommand(event.bookingId(), event.activityId(),
                event.reason()), Void.class);
    }
}
