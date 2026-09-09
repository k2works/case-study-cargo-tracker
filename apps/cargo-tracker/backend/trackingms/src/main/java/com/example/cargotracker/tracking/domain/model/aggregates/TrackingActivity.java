package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.CargoDeliveredEvent;
import com.example.cargotracker.shared.contract.event.CargoDeliveryRevertedEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.NotifyShipperOfExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.RegisterTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.ResolveTrackingExceptionCommand;
import com.example.cargotracker.tracking.domain.model.commands.StartExceptionResponseCommand;
import com.example.cargotracker.tracking.domain.model.entities.TrackingException;
import com.example.cargotracker.tracking.domain.model.events.ExceptionEscalatedEvent;
import com.example.cargotracker.tracking.domain.model.events.ExceptionResponseStartedEvent;
import com.example.cargotracker.tracking.domain.model.events.ExceptionShipperNotifiedEvent;
import com.example.cargotracker.tracking.domain.model.events.CargoMisroutedEvent;
import com.example.cargotracker.tracking.domain.model.events.DeferredHandlingAppliedEvent;
import com.example.cargotracker.tracking.domain.model.events.HandlingDeferredEvent;
import com.example.cargotracker.tracking.domain.model.events.HandlingNotAppliedEvent;
import com.example.cargotracker.tracking.domain.model.events.TrackingExceptionRegisteredEvent;
import com.example.cargotracker.tracking.domain.model.events.TrackingExceptionResolvedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.domain.model.valueobjects.ResponseStatus;
import com.example.cargotracker.tracking.domain.model.commands.RevertTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusRevertedEvent;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
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
        if (status == TransportStatus.EXCEPTION) {
            // **例外の対応中は預かる**（IT11 引き継ぎ枠 B）。順序としては正しく、
            // 状態が例外へ退避しているだけなので、解決したら適用しなければ
            // 事実と食い違ったまま残る——船に積んだ貨物が受領済に見える。
            appender.append(new HandlingDeferredEvent(trackingNumber.value(),
                    command.activityId(), command.handlingType(), command.unLocode(),
                    command.finalPort(), command.offRoute(), status, next,
                    command.operator(), command.completedAt(), clock.instant()));
            return;
        }
        if (!status.canTransitionTo(next)) {
            // **無言で捨てない**（IT9 レビュー M6）。記録は handlingms にあるが、
            // 追跡の履歴には何も残らず「荷役は記録したのに追跡が動いていない」と
            // いう問い合わせに答えられなかった。状態は動かさず、届いた事実だけ残す。
            //
            // **預かりとは分ける。** こちらは起きえない順序で届いたもので、
            // あとから適用してはいけない。
            appender.append(new HandlingNotAppliedEvent(trackingNumber.value(),
                    command.activityId(), command.handlingType(), command.unLocode(),
                    status, next, command.completedAt(), clock.instant()));
            return;
        }

        appender.append(new TransportStatusUpdatedEvent(trackingNumber.value(), status, next,
                StatusUpdateSource.HANDLING, command.activityId(), command.unLocode(),
                command.completedAt(), command.operator(), clock.instant()));
        afterAdvancing(next, new AppliedHandling(command.activityId(), command.handlingType(),
                command.unLocode(), command.operator(), command.completedAt()),
                appender, clock.instant());
    }

    /**
     * 状態を進めた<b>あと</b>にやること（US16 §受入基準 4 / US28 §受入基準 2）。
     *
     * <p><b>1 か所にまとめる。</b> 荷役は 2 つの経路で届く——その場で適用する
     * {@link #advance} と、例外の対応中に預かってから適用する
     * {@link #applyDeferredHandlings} である。片方にだけ書くと、**預かった荷役
     * だけ業務が止まる**——例外の対応中に引取が届いた貨物は、解決後に追跡だけ
     * 引取済になり、精算も予約も動かない（IT11 レビューで programmer が指摘）。</p>
     */
    private void afterAdvancing(TransportStatus next, AppliedHandling handling,
            EventAppender appender, java.time.Instant now) {
        String activityId = handling.activityId();
        String unLocode = handling.unLocode();
        java.time.Instant completedAt = handling.completedAt();
        if (next == TransportStatus.MISROUTED) {
            // **誤配は荷役が決める**（US28 §受入基準 2）。手で起票できないのは
            // そのため（ExceptionType#reportableByHand）——起きていない誤配を
            // 記録できると、経路設計者はそれを組み直そうとする。
            appender.append(new CargoMisroutedEvent(trackingNumber.value(), bookingId,
                    activityId, unLocode, completedAt, now));
            autoReportMisroute(handling, appender, now);
        }
        if (next == TransportStatus.DELIVERED) {
            // **精算の開始条件は別のイベントで出す**（US16 §受入基準 4）。
            // 1 つのイベントに「状態が変わった」と「精算を始めてよい」の 2 つの
            // 役割を持たせると、片方の都合でもう片方の購読側が動く。
            // 購読側は billingms（精算の開始）と bookingms（予約を引取済に）。
            appender.append(new CargoDeliveredEvent(trackingNumber.value(), bookingId,
                    completedAt, unLocode));
        }
    }

    /**
     * 誤配を自動で起票する（US28 §受入基準 2）。
     *
     * <p><b>識別子は荷役から導く。</b> 採番すると、同じ荷役から何度でも新しい例外が
     * できる（投影の主キーは例外の識別子なので、行も増える）。荷役の取り消しは
     * 反映済みの印を外すので、同じ荷役がもう一度届くことがある——そのとき
     * 起票済みの例外を重ねない。</p>
     *
     * <p><b>導いた識別子も UUID の形に収める。</b> {@code "MIS-" + activityId} のように
     * 前置きを足すと 36 文字を超え、投影の列（{@code exception_id VARCHAR(36)}）に
     * 入らない。**クラスタで初めて落ちる**——集約のテストは投影の桁を知らない
     * （IT11 の T6e で実測）。同じ荷役からは必ず同じ識別子が出る。</p>
     *
     * <p><b>誤配が続けて届いても重ならない。</b> 遷移表が {@code MISROUTED} から
     * {@code MISROUTED} を許さないので、2 度目の予定外の荷役はここまで来ない
     * （反映できなかった荷役として履歴に残る）。</p>
     */
    private void autoReportMisroute(AppliedHandling handling, EventAppender appender,
            java.time.Instant now) {
        String exceptionId = TrackingException.misrouteIdFor(handling.activityId());
        if (exceptions.containsKey(exceptionId)) {
            return;
        }
        TrackingException candidate = TrackingException.report(exceptionId,
                ExceptionType.MISROUTE, handling.completedAt(), handling.unLocode(),
                "予定ルート外の " + handling.unLocode() + " で "
                        + handling.handlingType() + " が記録されました");
        appender.append(new TrackingExceptionRegisteredEvent(trackingNumber.value(),
                candidate.exceptionId(), candidate.type().name(), candidate.occurredAt(),
                candidate.unLocode(), candidate.description(), candidate.urgent(),
                status, handling.operator(), now));
        appender.append(new TransportStatusUpdatedEvent(trackingNumber.value(), status,
                TransportStatus.EXCEPTION, StatusUpdateSource.EXCEPTION, null,
                handling.unLocode(), handling.completedAt(), handling.operator(), now));
    }

    /**
     * 輸送中の例外を起票する（UC16 / US19 §受入基準 1・2）。
     *
     * <p><b>戻る先を覚える</b>（不変条件 5）。起票した時点の状態を
     * {@code statusBeforeException} に写し、解決したらそこへ戻す。履歴から
     * 導き直すと、途中の荷役で状態が動いていたときに誤った先へ戻る。</p>
     *
     * <p><b>例外中でも起票できる。</b> 遅延の対応中に破損が見つかることはある。
     * 2 件目以降は状態を動かさない（すでに例外発生である）。</p>
     */
    @CommandHandler
    public void registerException(RegisterTrackingExceptionCommand command,
            EventAppender appender, Clock clock) {
        requireStarted(command.trackingNumber());
        if (exceptions.containsKey(command.exceptionId())) {
            // 同じ起票が二度届いた（自動起票は再配送されうる）。
            return;
        }
        // 値そのものの検査はエンティティが持つ（発生状況が無い起票を残さない）。
        TrackingException candidate = TrackingException.report(command.exceptionId(),
                command.type(), command.occurredAt(), command.unLocode(),
                command.description());
        var now = clock.instant();
        appender.append(new TrackingExceptionRegisteredEvent(trackingNumber.value(),
                candidate.exceptionId(), candidate.type().name(), candidate.occurredAt(),
                candidate.unLocode(), candidate.description(), candidate.urgent(),
                status, command.reportedBy(), now));

        if (candidate.urgent()) {
            // **緊急は上位者へ知らせる**（US20 §受入基準 3）。送信基盤はスコープ外
            // なので、残すのは知らせた事実と時刻だけ。読み口（S42 を管理者に開く）
            // と対で出す——記録だけでは受入基準の満たし方が成り立たない。
            appender.append(new ExceptionEscalatedEvent(trackingNumber.value(),
                    candidate.exceptionId(), candidate.type().name(), now));
        }
        if (status != TransportStatus.EXCEPTION) {
            appender.append(new TransportStatusUpdatedEvent(trackingNumber.value(), status,
                    TransportStatus.EXCEPTION, StatusUpdateSource.EXCEPTION, null,
                    command.unLocode(), command.occurredAt(), command.reportedBy(), now));
        }
    }

    /** 例外への対応を始める（UC16 / US19 §受入基準 4）。 */
    @CommandHandler
    public void startResponding(StartExceptionResponseCommand command,
            EventAppender appender, Clock clock) {
        requireStarted(command.trackingNumber());
        // 動かせるかはエンティティが答える（解決した例外はもう動かせない）。
        exceptionNamed(command.exceptionId()).requireModifiable();
        appender.append(new ExceptionResponseStartedEvent(trackingNumber.value(),
                command.exceptionId(), command.newEstimatedArrival(), command.plan(),
                command.respondedBy(), clock.instant()));
    }

    /**
     * 例外を解決する（UC16 / US19 §受入基準 4 / 不変条件 5・6）。
     *
     * <p><b>起票中の例外がすべて解決したときだけ戻す。</b> 1 件解決しただけで
     * 戻すと、まだ手を入れる場所が「正常」に見える。</p>
     */
    @CommandHandler
    public void resolveException(ResolveTrackingExceptionCommand command,
            EventAppender appender, Clock clock) {
        requireStarted(command.trackingNumber());
        var now = clock.instant();
        // 対応内容の無い解決を断るのもエンティティ（何をしたか読めない記録を残さない）。
        exceptionNamed(command.exceptionId()).requireResolvable(command.resolution());
        appender.append(new TrackingExceptionResolvedEvent(trackingNumber.value(),
                command.exceptionId(), command.resolution(), command.resolvedBy(), now));

        boolean othersOpen = exceptions.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(command.exceptionId()))
                .anyMatch(entry -> !entry.getValue().settled());
        if (!othersOpen && statusBeforeException != null) {
            // **戻り先を先に控える。** appender.append() はその場で集約へ適用されるので、
            // RESOLVED を積んだ瞬間に statusBeforeException は null に戻る。
            // あとから読むと「戻った先」が分からなくなる。
            TransportStatus restored = statusBeforeException;
            appender.append(new TransportStatusUpdatedEvent(trackingNumber.value(), status,
                    restored, StatusUpdateSource.RESOLVED, null, null,
                    now, command.resolvedBy(), now));
            applyDeferredHandlings(restored, appender, now);
        }
    }

    /**
     * 預かっていた荷役を、戻った先から順に適用する（IT11 引き継ぎ枠 B）。
     *
     * <p><b>届いた順に、1 つずつ状態を進める。</b> まとめて最後の状態へ飛ばすと、
     * 途中の区間が履歴から消える——荷主には「いつ船に載ったか」が答えられなくなる。</p>
     *
     * <p><b>預かったものが必ず適用できるとは限らない。</b> 戻った先から進めない
     * 荷役は、順序が入れ替わって届いたものなので、
     * {@link HandlingNotAppliedEvent} として履歴に残す。</p>
     *
     * <p>集約はイベントを追記するだけで、自分の {@code status} はまだ動いていない
     * （{@code EventSourcingHandler} は追記後に走る）。だから進行中の状態を
     * 引数で持ち回る。</p>
     */
    private void applyDeferredHandlings(TransportStatus restored, EventAppender appender,
            java.time.Instant now) {
        TransportStatus current = restored;
        // **控えてから回す。** append() は即座に適用されるので、
        // DeferredHandlingAppliedEvent が反復中の map を削る。
        for (DeferredHandling deferred : List.copyOf(deferredHandlings.values())) {
            TransportStatus next = TransportStatus.afterHandling(
                    deferred.handlingType(), deferred.finalPort(), deferred.offRoute());
            if (current.canTransitionTo(next)) {
                appender.append(new TransportStatusUpdatedEvent(trackingNumber.value(), current,
                        next, StatusUpdateSource.HANDLING, deferred.activityId(),
                        deferred.unLocode(), deferred.completedAt(), deferred.operator(), now));
                // **その場で適用したときと同じことをする。** 片方にだけ書くと、
                // 預かった引取が精算へ伝わらない（IT11 レビュー 高）。
                afterAdvancing(next, new AppliedHandling(deferred.activityId(),
                        deferred.handlingType(), deferred.unLocode(), deferred.operator(),
                        deferred.completedAt()), appender, now);
                current = next;
            } else {
                appender.append(new HandlingNotAppliedEvent(trackingNumber.value(),
                        deferred.activityId(), deferred.handlingType(), deferred.unLocode(),
                        current, next, deferred.completedAt(), now));
            }
            appender.append(new DeferredHandlingAppliedEvent(trackingNumber.value(),
                    deferred.activityId()));
        }
    }

    /**
     * 荷主へ知らせた事実を記録する（UC16 / US19 §受入基準 3）。
     *
     * <p><b>送信基盤はスコープ外</b>（ui_design.md:120）。残すのは
     * 「いつ・どうやって・何を伝えたか」だけである。</p>
     */
    @CommandHandler
    public void notifyShipperOfException(NotifyShipperOfExceptionCommand command,
            EventAppender appender, Clock clock) {
        requireStarted(command.trackingNumber());
        // 起票されていない例外への通知は残さない（どの例外の話か分からない記録になる）。
        exceptionNamed(command.exceptionId()).requireModifiable();
        appender.append(new ExceptionShipperNotifiedEvent(trackingNumber.value(),
                command.exceptionId(), command.means(), command.summary(),
                command.notifiedBy(), clock.instant()));
    }

    /**
     * 追跡が始まっているか。
     *
     * <p>始まっていない追跡に例外を起票させない——起票だけが残り、
     * どの貨物の話か分からない記録になる。</p>
     */
    private void requireStarted(String number) {
        if (trackingNumber == null) {
            throw new IllegalTransition("追跡 " + number + " は始まっていません");
        }
    }

    /** 起票済みの例外。<b>知らない例外を 500 にしない</b>（入力の誤り）。 */
    private TrackingException exceptionNamed(String exceptionId) {
        TrackingException exception = exceptions.get(exceptionId);
        if (exception == null) {
            throw new BusinessRuleViolation("例外 " + exceptionId + " は起票されていません");
        }
        return exception;
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
        if (trackingNumber == null) {
            return;
        }
        if (deferredHandlings.containsKey(command.activityId())) {
            // **預かり中の荷役は、預かりから外して終わる**（IT11 レビュー 高）。
            // 預かった荷役はまだ状態を進めていないので、下の「戻す」経路には
            // 掛からない——外さないと、例外を解決した瞬間に**取り消された荷役が
            // 適用され**、起きなかったことが履歴に積まれる。リプレイでも同じ。
            appender.append(new DeferredHandlingAppliedEvent(trackingNumber.value(),
                    command.activityId()));
            return;
        }
        if (statusBeforeHandling == null) {
            // 進めていないものは戻せない。荷役の取り消しは handlingms に残る。
            return;
        }
        if (lastHandlingActivityId != null
                && !lastHandlingActivityId.equals(command.activityId())) {
            // 最後の荷役ではない。**戻さないことが正しい**——そのあとの荷役で
            // 進んだ先のほうが、いま貨物が置かれている状態に近い。
            return;
        }

        boolean wasDelivered = status == TransportStatus.DELIVERED;
        appender.append(new TransportStatusRevertedEvent(trackingNumber.value(), status,
                statusBeforeHandling, command.handlingType(), command.reason(),
                command.revertedBy(), clock.instant()));
        if (wasDelivered) {
            // **引取済から出るときだけ打ち消す**（IT11 引き継ぎ枠 A）。IT10 は
            // 打ち消しを購読側へ伝える手立てが無かったので、取り消し自体を断って
            // 塞いでいた。状態を戻すイベントとは別に出す——束ねると、状態の
            // 巻き戻しのたびに精算が動く。
            appender.append(new CargoDeliveryRevertedEvent(trackingNumber.value(), bookingId,
                    clock.instant(), command.reason()));
        }
    }

    /** 荷役で進める前の状態。取り消しの戻し先（不変条件 11）。 */
    private TransportStatus statusBeforeHandling;

    /**
     * 例外の起票前の状態。<b>解決したらここへ戻す</b>（不変条件 5）。
     *
     * <p>履歴から導き直さない——途中の荷役で状態が動いていたときに誤った先へ戻る。</p>
     */
    private TransportStatus statusBeforeException;

    /** 起票された例外。解決しても消さない（不変条件 6）。 */
    private final java.util.Map<String, TrackingException> exceptions =
            new java.util.LinkedHashMap<>();

    @EventSourcingHandler
    void on(TrackingExceptionRegisteredEvent event) {
        // **復元では検査しない**（不変条件の追加は既存行を壊す）。report() に
        // 新しい必須項目を足した日、過去のイベントを持つ集約が復元できなくなる。
        // 検査は新規受け入れ（registerException）の側にだけ置く。
        exceptions.put(event.exceptionId(), new TrackingException(event.exceptionId(),
                ExceptionType.valueOf(event.exceptionType()), event.occurredAt(),
                event.unLocode(), event.description(), ResponseStatus.REPORTED, null, null));
        if (statusBeforeException == null) {
            // **最初の起票の時点を覚える。** 2 件目は例外発生から起票されるので、
            // 上書きすると戻る先が EXCEPTION になる。
            statusBeforeException = event.statusBeforeException();
        }
    }

    @EventSourcingHandler
    void on(ExceptionResponseStartedEvent event) {
        exceptions.computeIfPresent(event.exceptionId(),
                (id, exception) -> exception.startResponding());
    }

    @EventSourcingHandler
    void on(TrackingExceptionResolvedEvent event) {
        exceptions.computeIfPresent(event.exceptionId(),
                (id, exception) -> exception.resolve(event.resolution(), event.resolvedAt()));
    }

    /**
     * 例外の対応中に預かった荷役（IT11 引き継ぎ枠 B）。
     *
     * <p><b>届いた順を保つ</b>（{@code LinkedHashMap}）。適用は 1 つずつ順に行う。</p>
     */
    private final java.util.Map<String, DeferredHandling> deferredHandlings =
            new java.util.LinkedHashMap<>();

    /**
     * 適用した荷役 1 件。
     *
     * <p><b>引数の群れをまとめる。</b> 「その場で適用」と「預かってから適用」の
     * 2 経路が同じ 5 つの値を渡すので、束ねないと引数の並び違いが起きる。</p>
     */
    private record AppliedHandling(String activityId, String handlingType, String unLocode,
            String operator, java.time.Instant completedAt) {
    }

    /** 預かった荷役 1 件。適用に要るものを全部持つ。 */
    private record DeferredHandling(String activityId, String handlingType, String unLocode,
            boolean finalPort, boolean offRoute, String operator,
            java.time.Instant completedAt) {
    }

    @EventSourcingHandler
    void on(HandlingDeferredEvent event) {
        deferredHandlings.put(event.activityId(), new DeferredHandling(event.activityId(),
                event.handlingType(), event.unLocode(), event.finalPort(), event.offRoute(),
                event.operator(), event.completedAt()));
    }

    @EventSourcingHandler
    void on(DeferredHandlingAppliedEvent event) {
        // 預かりを解く。**残したままにすると、解決のたびに同じ荷役を適用し直す。**
        deferredHandlings.remove(event.activityId());
    }

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
        if (event.source() == StatusUpdateSource.RESOLVED) {
            // 例外前へ戻った。次の起票でまた覚え直す。
            this.statusBeforeException = null;
        }
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
