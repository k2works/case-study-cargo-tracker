package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.CargoDeliveredEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.RevertTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusRevertedEvent;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 貨物の追跡（UC12 / US14）。<b>trackingms の最初の集約</b>。
 *
 * <p>bookingms が追跡番号を発行すると、{@code BookingReactionHandler} が
 * {@link InitializeTrackingCommand}（契約コマンド）を送って追跡が始まる。</p>
 *
 * <p><b>状態は載って来ない。</b> 追跡を始めた直後がどの状態か（{@code NOT_RECEIVED}）は
 * ここが決める。送る側が相手の状態機械を知っていることにしない。</p>
 *
 * <p><b>不変条件: 二重に開始しない。</b> 連鎖は失敗したら再試行するので、同じコマンドが
 * 2 度届きうる。追跡が 2 つできると、荷役がどちらに付くのか決まらない。</p>
 */
@EventSourced(idType = String.class, tagKey = "trackingNumber")
public class TrackingActivity {

    private TrackingNumber trackingNumber;
    private String bookingId;
    private TransportStatus status;

    @EntityCreator
    public TrackingActivity() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 追跡を開始する（US14 §受入基準 3）。
     *
     * <p><b>static ではなくインスタンスのハンドラにする。</b> 両方置くと、集約が既に
     * 存在しても static のほうが呼ばれ、2 度目の開始が通る（bookingms の IT2 で実測）。</p>
     *
     * <p><b>開始した時刻は {@code Clock} で決める。</b> 発行時刻（{@code issuedAt}）を
     * そのまま使うと、連鎖が数時間止まっていたときに「止まっていなかった」ように見える。</p>
     */
    @CommandHandler
    public String initialize(InitializeTrackingCommand command, EventAppender appender,
            Clock clock) {
        if (trackingNumber != null) {
            throw new IllegalTransition(
                    "追跡 " + trackingNumber.value() + " は既に開始しています");
        }
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            // 誰の荷物か辿れない追跡は、荷役を記録しても業務に繋がらない。
            throw new BusinessRuleViolation("予約 ID は必須です");
        }
        if (command.legs().isEmpty()) {
            // 追跡番号は経路が決まってから発行される。旅程が無いのは、途中で
            // 落としたということ（IT6 の「値は全層を生き延びるか確かめる」）。
            throw new BusinessRuleViolation("旅程は必須です");
        }
        // 追跡番号そのものの検査は値オブジェクトが持つ（空なら断る）。
        TrackingNumber number = TrackingNumber.of(command.trackingNumber());

        // **コマンドで届いた値を載せ直す。** 投影はイベントからしか作れないので、
        // ここで落とすと追跡の一覧に出発地も目的地も出せない（IT6 の「値は全層を
        // 生き延びるか確かめる」）。
        appender.append(new TrackingInitializedEvent(number.value(), command.bookingId(),
                command.shipperId(), command.originUnLocode(), command.destinationUnLocode(), command.cargoType(),
                command.legs().stream().map(leg -> new TrackingInitializedEvent.Leg(
                        leg.voyageNumber(), leg.loadUnLocode(), leg.unloadUnLocode(),
                        leg.loadTime(), leg.unloadTime())).toList(),
                clock.instant()));
        return number.value();
    }

    /**
     * 輸送状態を手で更新する（UC14 / US17 §受入基準 2）。
     *
     * <p><b>遷移の可否は {@link TransportStatus#canTransitionTo} に聞く。</b> ここで
     * 判定を書き直すと、判定が 2 つになり片方だけ直る。</p>
     *
     * <p><b>例外発生中は動かさない</b>（不変条件 5 の下地）。例外の解決は「例外前の
     * 状態へ戻る」ことなので、解決を待たずに手で動かすと戻り先と実際が食い違う。
     * 例外そのものの起票・解決は US19・US20（IT9 以降）で足す。</p>
     */
    @CommandHandler
    public void updateStatusManually(UpdateTransportStatusCommand command,
            EventAppender appender, Clock clock) {
        if (trackingNumber == null) {
            throw new IllegalTransition("追跡 " + command.trackingNumber() + " は始まっていません");
        }
        if (command.updatedBy() == null || command.updatedBy().isBlank()) {
            // 誰が動かしたか分からない記録は、後から突き合わせられない。
            throw new BusinessRuleViolation("更新者は必須です");
        }
        if (command.newStatus() == null) {
            throw new BusinessRuleViolation("新しい状態は必須です");
        }
        if (status == TransportStatus.EXCEPTION) {
            throw new BusinessRuleViolation(
                    "例外の対応中は状態を手で動かせません。例外を解決してください");
        }
        if (!command.newStatus().isSetByHand()) {
            // 誤配は荷役が、例外発生は例外の起票が決める。手で入れると、起きて
            // いない誤配を記録できてしまう。
            throw new BusinessRuleViolation(
                    command.newStatus().label() + " は手では入れられません");
        }
        // **読み口と同じ述語で判断する。** 別々に持つと、画面が「押しても断られる先」
        // を出す（IT8 のレビューで実際に出た）。
        if (!TransportStatus.manualTransitionsFrom(status).contains(command.newStatus())) {
            throw new IllegalTransition(status.label() + " から "
                    + command.newStatus().label() + " へは動かせません");
        }
        appender.append(new TransportStatusUpdatedEvent(trackingNumber.value(), status,
                command.newStatus(), StatusUpdateSource.MANUAL, null, command.location(),
                command.occurredAt(), command.updatedBy(), clock.instant()));
    }

    /**
     * 荷役の記録から貨物状態を進める（UC14 / US15 §受入基準 4）。
     *
     * <p><b>進めた先は種別と港が決める</b>（{@link TransportStatus#afterHandling}）。
     * ここで判定を書き直さない。</p>
     *
     * <p><b>遷移表が許さない先へは動かさない</b>（不変条件 2）。荷役の順序が
     * 現場で入れ替わることはあるが、状態を飛ばして進めると履歴が事実と食い違う。
     * <b>断らずに記録だけ残す</b>——荷役そのものは handlingms に記録済みで、
     * ここで例外にすると Event Processor が止まり、後続の荷役まで届かなくなる。</p>
     *
     * <p><b>例外の対応中は進めない</b>（不変条件 5 の下地）。解決は例外の側で行う。</p>
     *
     * <p><b>同じ荷役が二度届いても 1 度しか進めない。</b> Event Processor は
     * at-least-once である。遷移表は同一状態への更新を弾くが<b>識別子は見ていない</b>——
     * 積込→荷降しと進んだあとに古い積込がもう一度届くと、荷降し済→積込済は
     * 遷移表が許すので、起きていない積込が履歴に積まれる。</p>
     */
    @CommandHandler
    public void advance(AdvanceTrackingCommand command, EventAppender appender, Clock clock) {
        if (trackingNumber == null) {
            // **知らない追跡番号の荷役では止まらない**（不変条件 8）。荷役は
            // すでに記録されており、ここで例外にすると後続の荷役まで止まる。
            return;
        }
        if (appliedActivities.contains(command.activityId())) {
            // すでに反映した荷役。再配送・リプレイで二度目が届いている。
            return;
        }
        TransportStatus next = TransportStatus.afterHandling(
                command.handlingType(), command.finalPort(), command.offRoute());
        if (status == TransportStatus.EXCEPTION || !status.canTransitionTo(next)) {
            // 進められない。荷役の記録は handlingms に残っているので、
            // ここでは何もしない（黙って捨てない——記録は向こうにある）。
            return;
        }

        appender.append(new TransportStatusUpdatedEvent(trackingNumber.value(), status, next,
                StatusUpdateSource.HANDLING, command.activityId(), command.unLocode(),
                command.completedAt(), command.operator(), clock.instant()));

        if (next == TransportStatus.DELIVERED) {
            // **精算の開始条件は別のイベントで出す**（US16 §受入基準 4）。
            // 1 つのイベントに「状態が変わった」と「精算を始めてよい」の 2 つの
            // 役割を持たせると、片方の都合でもう片方の購読側が動く。
            // 購読側は billingms（精算の開始）と bookingms（予約を引取済に）。
            appender.append(new CargoDeliveredEvent(trackingNumber.value(), bookingId,
                    command.completedAt(), command.unLocode()));
        }
    }

    /**
     * 取り消された荷役の分だけ戻す（UC13 / 不変条件 11）。
     *
     * <p><b>戻す先は「その荷役で進める前の状態」。</b> 集約が覚えている。</p>
     *
     * <p><b>戻せるのは最後に進めた荷役だけ。</b> 契約イベントは順序が入れ替わる
     * ことがある（{@code HandlingActivityVoidedEvent}）。受領→積込と進んだあとで
     * <b>古い受領</b>を取り消したとき、覚えている 1 段だけを見て戻すと、実際には
     * 積込済の貨物が受領済に見える。取り消された荷役を名指しで照合する。</p>
     */
    @CommandHandler
    public void revert(RevertTrackingCommand command, EventAppender appender, Clock clock) {
        if (trackingNumber == null || statusBeforeHandling == null) {
            // 進めていないものは戻せない。荷役の取り消しは handlingms に残る。
            return;
        }
        if (lastHandlingActivityId != null
                && !lastHandlingActivityId.equals(command.activityId())) {
            // 最後の荷役ではない。**戻さないことが正しい**——そのあとの荷役で
            // 進んだ先のほうが、いま貨物が置かれている状態に近い。
            return;
        }

        appender.append(new TransportStatusRevertedEvent(trackingNumber.value(), status,
                statusBeforeHandling, command.handlingType(), command.reason(),
                command.revertedBy(), clock.instant()));
    }

    /** 荷役で進める前の状態。取り消しの戻し先（不変条件 11）。 */
    private TransportStatus statusBeforeHandling;

    /** 最後に状態を進めた荷役。取り消しはこれと一致するときだけ戻す。 */
    private String lastHandlingActivityId;

    /**
     * 反映済みの荷役。<b>再配送を弾く鍵</b>。
     *
     * <p>取り消したものは外す——取り消しの取り消し（同じ荷役の再記録）は
     * handlingms で新しい {@code activityId} になるが、
     * <b>戻したあとに同じ荷役が届いたら進め直せる</b>ほうが事実に近い。</p>
     */
    private final Set<String> appliedActivities = new HashSet<>();

    @EventSourcingHandler
    void on(TransportStatusUpdatedEvent event) {
        if (event.source() == StatusUpdateSource.HANDLING) {
            this.appliedActivities.add(event.activityId());
            // 戻し先は「その荷役で進める前」。手動更新では覚えない
            // （手動の取り消しという操作が無い）。
            this.statusBeforeHandling = event.previousStatus();
            this.lastHandlingActivityId = event.activityId();
        }
        this.status = event.newStatus();
    }

    @EventSourcingHandler
    void on(TransportStatusRevertedEvent event) {
        this.status = event.restoredStatus();
        this.appliedActivities.remove(lastHandlingActivityId);
        this.statusBeforeHandling = null;
        this.lastHandlingActivityId = null;
    }

    @EventSourcingHandler
    void on(TrackingInitializedEvent event) {
        this.trackingNumber = TrackingNumber.of(event.trackingNumber());
        this.bookingId = event.bookingId();
        this.status = TransportStatus.NOT_RECEIVED;
    }

    /** 復元した輸送状態。荷役（US15・IT9）が読む。 */
    public TransportStatus status() {
        return status;
    }

    /** 元の予約。荷主向けの追跡（US18・IT8）が読む。 */
    public String bookingId() {
        return bookingId;
    }
}
