package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.booking.domain.model.commands.AdjustRouteSpecificationCommand;
import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.commands.NotifyShipperCommand;
import com.example.cargotracker.booking.domain.model.commands.ReturnToRoutingCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestConditionReviewCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestRoutingCommand;
import com.example.cargotracker.booking.domain.model.commands.RespondToConditionReviewCommand;
import com.example.cargotracker.booking.domain.model.commands.RevertTrackingNumberCommand;
import com.example.cargotracker.booking.domain.model.commands.UpdateCargoSpecificationCommand;
import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.ReturnedToRoutingEvent;
import com.example.cargotracker.booking.domain.model.events.ShipperNotifiedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRespondedEvent;
import com.example.cargotracker.booking.domain.model.events.RouteSpecificationAdjustedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.commands.AssignRouteCommand;
import com.example.cargotracker.booking.domain.model.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.domain.model.commands.IssueTrackingNumberCommand;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberRevertedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 貨物予約（UC03 / US04・US05）。
 *
 * <p>状態を持つ最初の集約であり、イベント列からの復元が判断に効く。IT2 で到達するのは
 * {@code PRELIMINARY} までだが、遷移の判定は {@link BookingStatus#canTransitionTo} に
 * 置き、あとの IT で足すたびに書き足す場所が増えないようにする。</p>
 *
 * <p>不変条件は domain-model.md「Cargo 集約の不変条件」が正典。1（BookingId は不変・
 * ShipperId 必須）はここが、2（出発地 ≠ 目的地）は {@code RouteSpecification} が、
 * 3（危険物の申告・温度条件）は {@code CargoSpecification} が、8（二重発行の禁止）は
 * {@code issueTrackingNumber} が守る。</p>
 */
@EventSourced(idType = String.class, tagKey = "bookingId")
public class Cargo {

    private String bookingId;
    private BookingStatus bookingStatus;
    private RoutingStatus routingStatus;
    /** 受け付けたときの到着期限。修正で期限を触ったかどうかの判断に要る。 */
    private LocalDate arrivalDeadline;
    /**
     * 経路仕様の端点。旅程が仕様を満たすかの判断（不変条件 5）に要る。
     *
     * <p><b>修正（US32）でも書き換える。</b> 受付時の値だけを覚えていると、
     * 目的地を直した予約に古い目的地の経路が付く。</p>
     */
    private Location origin;
    private Location destination;
    /** 貨物種別。追跡番号の発行（US14）で trackingms へ渡す。 */
    private String cargoType;
    /** 発行済みの追跡番号。取り消し（補償）で「何を取り消したか」を残すのに要る。 */
    private String trackingNumber;
    /** 営業へ差し戻していて、まだ返事が来ていないか（US10 §4 の対）。 */
    private boolean awaitingConditionReviewResponse;
    /** 確定した旅程。<b>発行のイベントに載せる</b>（IT9 の荷役が材料にする）。 */
    private List<CargoRoutedEvent.Leg> legs = List.of();

    @EntityCreator
    public Cargo() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 予約を受け付ける。
     *
     * <p><b>static ではなくインスタンスのハンドラにしている。</b> 両方置くと、集約が
     * 既に存在しても static のほうが呼ばれ、2 度目の受付が通る（IT2 で実測）。
     * {@code @EntityCreator} が空の集約を用意するので、片方で両方を扱える。</p>
     */
    @CommandHandler
    public String book(BookCargoCommand command, EventAppender appender, Clock clock) {
        if (bookingId != null) {
            // 復元した集約が既に予約を持っているのに受け付けると、イベント列に
            // 予約が 2 本並び、どちらが正か決まらない。
            throw new IllegalTransition("予約 " + bookingId + " は既に受け付けています");
        }
        // 業務タイムゾーンの「今日」で判断する。JVM 既定だと、日本時間の朝 9 時より
        // 前に受け付けた予約で当日の期限が「過去」になる時間帯ができる。
        CargoValidation.validate(command, LocalDate.now(clock));
        appender.append(CargoBookedEvent.of(command.bookingId(), command.shipperId(),
                command.routeSpecification(), command.cargoSpecification(),
                command.bookedBy()));
        return command.bookingId();
    }

    /**
     * 経路設計者に引き渡す（UC04 / US06）。
     *
     * <p><b>遷移の判定は書き直さず {@link BookingStatus#canTransitionTo} を呼ぶ。</b>
     * IT2 で置いた遷移表を初めて使う場所。ここで {@code if (status == PRELIMINARY)} と
     * 書くと、遷移表と集約の判断が二重になり、片方だけ直したときに食い違う。</p>
     */
    @CommandHandler
    public String requestRouting(RequestRoutingCommand command, EventAppender appender) {
        requireBooked(command.bookingId());
        // 遷移先で判断しない。ROUTE_PROPOSED への自己遷移は経路の確定と条件の調整の
        // もので、引き渡しではない。述語を呼ぶ（BookingStatus#canRequestRouting）。
        if (!bookingStatus.canRequestRouting()) {
            throw new IllegalTransition(
                    "状態 " + bookingStatus.label() + " の予約は経路設計へ引き渡せません");
        }
        appender.append(new RoutingRequestedEvent(command.bookingId(), command.requestedBy()));
        return command.bookingId();
    }

    /**
     * 入力の誤りを直す（UC03・UC04 / US32）。
     *
     * <p><b>直せるかどうかは遷移表の述語を呼ぶ</b>（{@link BookingStatus#canUpdateSpecification}）。
     * ここで書き直すと、状態が増えたときに集約と遷移表の判断が食い違う。</p>
     *
     * <p><b>登録と同じ検査を通す。</b> 書き直すと「登録では断るのに修正では通る」が
     * 生まれる（{@code CargoValidation} が両方を守る）。</p>
     */
    @CommandHandler
    public String updateSpecification(UpdateCargoSpecificationCommand command,
            EventAppender appender, Clock clock) {
        requireBooked(command.bookingId());
        if (!bookingStatus.canUpdateSpecification()) {
            throw new IllegalTransition("状態 " + bookingStatus.label() + " の予約は修正できません");
        }
        // **期限は「変えたときだけ」検査する。** 据え置きにも今日以降を求めると、
        // 期限を過ぎた仮受付の予約は品名すら直せなくなる（誤りに気づくのは
        // たいてい期限が近づいてからで、そのときには直せない）。
        CargoValidation.validate(command.cargoSpecification(), command.routeSpecification(),
                LocalDate.now(clock), arrivalDeadline);

        appender.append(CargoSpecificationUpdatedEvent.of(command.bookingId(),
                command.routeSpecification(), command.cargoSpecification(),
                command.updatedBy(), clock.instant()));
        return command.bookingId();
    }

    /**
     * 選んだ経路を確定する（UC07 / US09）。
     *
     * <p><b>旅程が経路仕様を満たすかは集約が見る</b>（不変条件 5）。「候補は探索が
     * 作ったのだから正しい」としない——探索と集約は別の判断で、API を直接叩く経路も
     * ある。区間の連結と時刻の昇順は {@link CargoItinerary} が守る（不変条件 4）。</p>
     *
     * <p><b>{@code BookingStatus} は動かさない。</b> 荷主に通知するまでは提案中である。</p>
     */
    @CommandHandler
    public String assignRoute(AssignRouteCommand command, EventAppender appender, Clock clock) {
        requireBooked(command.bookingId());
        // 判定は書き直さず述語を呼ぶ（RoutingStatus#canAssignRoute）。画面も同じ
        // 判断を写しており、canon テストが述語の本体を読んで突き合わせる。
        if (!routingStatus.canAssignRoute()) {
            throw new IllegalTransition(
                    "経路設計を依頼していない予約には経路を確定できません（"
                            + routingStatus.label() + "）");
        }
        if (command.itinerary() == null) {
            throw new BusinessRuleViolation("旅程は必須です");
        }
        if (!routeSpecification().isSatisfiedBy(command.itinerary(), clock.getZone())) {
            // 不変条件 5。期限も端点も、いま集約が持っている値で見る。
            throw new BusinessRuleViolation(
                    "選んだ旅程は予約の経路仕様を満たしません（期限 " + arrivalDeadline
                            + " / " + origin.unLocode().value() + " → "
                            + destination.unLocode().value() + "）");
        }

        appender.append(CargoRoutedEvent.of(command.bookingId(), command.itinerary(),
                command.assignedBy(), clock.instant()));
        return command.bookingId();
    }

    /**
     * 経路の条件を調整する（UC08 / US10）。
     *
     * <p><b>調整を集約に記録する。</b> 画面の一時的な絞り込みにすると、誰がいつ期限を
     * 延ばしたかが残らない（UC08 の最低保証「調整条件と再算出結果が記録される」）。</p>
     *
     * <p><b>経路設計に入ってからだけ開く。</b> 仮受付のあいだは S24（予約修正）が正典で、
     * 2 つの入口を同時に開くと「どちらが正か」が読めない。逆に経路設計に入った予約は
     * S24 が使えないので、期限を延ばす手段はここだけである。</p>
     *
     * <p><b>確定済みの旅程は消さない。</b> 戻すのは {@code routingStatus} だけ
     * （ROUTED のままだと条件を変えても確定し直せない）。</p>
     */
    @CommandHandler
    public String adjustRouteSpecification(AdjustRouteSpecificationCommand command,
            EventAppender appender, Clock clock) {
        requireBooked(command.bookingId());
        // 誤配を含めない（調整が誤配の記録を消し、US28 の設計を先に縛る）。
        if (!routingStatus.canAdjustRouteSpecification()) {
            throw new IllegalTransition(
                    "この予約の条件は調整できません（" + routingStatus.label() + "）");
        }
        LocalDate deadline = command.arrivalDeadline();
        if (deadline == null) {
            throw new BusinessRuleViolation("到着期限は必須です");
        }
        if (deadline.isBefore(LocalDate.now(clock))) {
            // 過去の期限にすると、どの候補も期限切れになる。組めない条件を作らせない。
            throw new BusinessRuleViolation("到着期限は今日以降にしてください: " + deadline);
        }
        // **港コードは Location に通す。** 生文字列だと小文字が端点の除外検査を
        // 素通りし、長すぎる起点は投影の UPDATE で落ちてリプレイのたびに落ち続ける。
        List<Location> excluded = (command.excludeUnLocodes() == null
                ? List.<String>of() : command.excludeUnLocodes()).stream()
                .map(Location::of)
                .toList();
        for (Location port : excluded) {
            // 必ず通る港を除外すると、候補は必ず 0 件になる。条件を変えても直らない
            // ものを、変え続けさせることになる。
            if (port.equals(origin) || port.equals(destination)) {
                throw new BusinessRuleViolation(
                        "出発地と目的地は除外できません: " + port.unLocode().value());
            }
        }
        Location departFrom = command.departFromUnLocode() == null
                ? null : Location.of(command.departFromUnLocode());

        appender.append(new RouteSpecificationAdjustedEvent(command.bookingId(), deadline,
                excluded.stream().map(port -> port.unLocode().value()).toList(),
                departFrom == null ? null : departFrom.unLocode().value(),
                command.adjustedBy(), clock.instant()));
        return command.bookingId();
    }

    /**
     * 条件では組めないことを営業へ差し戻す（UC08 / US10 §受入基準 4）。
     *
     * <p><b>状態は動かさない</b>（ADR-0009 決定 1）。{@code NOT_ROUTED} へ戻すと
     * 「一度も設計していない予約」と区別が付かなくなり、経路設計作業一覧（S30）と
     * 誤配の扱いにも波及する。記録で表し、営業のダッシュボードに出す。</p>
     *
     * <p>差し戻せる状態の判断は {@link RoutingStatus#canRequestConditionReview()} に
     * 置く。ここに条件を書き直すと、画面と食い違う。</p>
     */
    @CommandHandler
    public String requestConditionReview(RequestConditionReviewCommand command,
            EventAppender appender, Clock clock) {
        requireBooked(command.bookingId());
        if (!routingStatus.canRequestConditionReview()) {
            throw new IllegalTransition(
                    "この予約は営業へ差し戻せません（" + routingStatus.label() + "）");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            // 理由が無いと、営業は荷主と何を協議すればよいのか分からない。
            throw new BusinessRuleViolation("差し戻す理由は必須です");
        }

        appender.append(new ConditionReviewRequestedEvent(command.bookingId(),
                command.reason().trim(), command.requestedBy(), clock.instant()));
        return command.bookingId();
    }

    /**
     * 確定した経路を荷主へ通知した記録を残す（UC10 / US12）。
     *
     * <p><b>送信基盤はスコープ外だが、記録は業務の守りとして働く。</b> 経路が
     * 決まっていない予約は通知できず、通知した予約だけが確定（US13）へ進める。
     * 再通知は許す（条件が変わったら伝え直す）。</p>
     */
    @CommandHandler
    public String notifyShipper(NotifyShipperCommand command, EventAppender appender,
            Clock clock) {
        requireBooked(command.bookingId());
        // 判定は書き直さず述語を呼ぶ（RoutingStatus#canNotifyShipper）。
        if (!routingStatus.canNotifyShipper()) {
            throw new IllegalTransition(
                    "経路が決まっていない予約は荷主へ通知できません（"
                            + routingStatus.label() + "）");
        }
        // **予約の状態も見る。** routingStatus だけだと、確定済みや終端の予約への
        // 再通知で、遷移表に無い後退が静かに起きる。
        if (!bookingStatus.canTransitionTo(BookingStatus.ROUTE_NOTIFIED)) {
            throw new IllegalTransition(
                    "状態 " + bookingStatus.label() + " の予約は荷主へ通知できません");
        }
        if (command.recipientEmail() == null || command.recipientEmail().isBlank()) {
            throw new BusinessRuleViolation("通知の宛先は必須です");
        }
        if (command.summary() == null || command.summary().isBlank()) {
            // 荷主から「聞いていない」と言われたときに突き合わせられない。
            throw new BusinessRuleViolation("通知内容は必須です");
        }

        appender.append(new ShipperNotifiedEvent(command.bookingId(),
                command.recipientEmail().trim(), command.summary().trim(),
                command.notifiedBy(), clock.instant()));
        return command.bookingId();
    }

    @EventSourcingHandler
    void on(ShipperNotifiedEvent event) {
        this.bookingStatus = BookingStatus.ROUTE_NOTIFIED;
    }

    /**
     * 予約を確定する（UC11 / US13）。営業が荷主の承認を確認してから使う。
     *
     * <p><b>通知していない予約は確定できない。</b> 遷移表は
     * {@code ROUTE_NOTIFIED → CONFIRMED} だけを許す。荷主が知らないうちに確定すると、
     * 追跡番号の発行と輸送手配まで進む。二重の確定も同じ判定で断る
     * （{@code CONFIRMED → CONFIRMED} は遷移表に無い）。</p>
     */
    @CommandHandler
    public String confirm(ConfirmBookingCommand command, EventAppender appender, Clock clock) {
        requireBooked(command.bookingId());
        if (!bookingStatus.canTransitionTo(BookingStatus.CONFIRMED)) {
            throw new IllegalTransition(
                    "状態 " + bookingStatus.label() + " の予約は確定できません"
                            + "（荷主へ通知してから確定してください）");
        }

        appender.append(new BookingConfirmedEvent(command.bookingId(),
                command.confirmedBy(), clock.instant()));
        return command.bookingId();
    }

    @EventSourcingHandler
    void on(BookingConfirmedEvent event) {
        this.bookingStatus = BookingStatus.CONFIRMED;
    }

    /**
     * 追跡番号を発行する（UC12 / US14）。<b>経路設計者の操作</b>。
     *
     * <p><b>不変条件 8: 二重に発行しない。</b> 発行から連鎖が始まるので、2 度発行すると
     * 追跡が 2 つできる。遷移表が {@code CONFIRMED → TRACKING_ISSUED} だけを許すので、
     * 未確定の発行も二重の発行も同じ判定で断る。</p>
     *
     * <p><b>採番はしない</b>（同時に 2 件発行したときに同じ番号が出る）。投影が採る。</p>
     */
    @CommandHandler
    public String issueTrackingNumber(IssueTrackingNumberCommand command,
            EventAppender appender, Clock clock) {
        requireBooked(command.bookingId());
        if (!bookingStatus.canTransitionTo(BookingStatus.TRACKING_ISSUED)) {
            throw new IllegalTransition(
                    "状態 " + bookingStatus.label() + " の予約に追跡番号は発行できません");
        }
        if (command.trackingNumber() == null || command.trackingNumber().isBlank()) {
            throw new BusinessRuleViolation("追跡番号は必須です");
        }

        appender.append(TrackingNumberIssuedEvent.of(command.bookingId(),
                command.trackingNumber().trim(), origin.unLocode().value(),
                destination.unLocode().value(), cargoType, legs,
                command.issuedBy(), clock.instant()));
        return command.bookingId();
    }

    @EventSourcingHandler
    void on(TrackingNumberIssuedEvent event) {
        this.bookingStatus = BookingStatus.TRACKING_ISSUED;
        this.trackingNumber = event.trackingNumber();
    }

    /**
     * 追跡番号の発行を取り消す（US14 の補償 / ADR-0010 決定 4）。
     *
     * <p>trackingms へ追跡開始が届かないまま再試行の上限を超えたときに、調整役が送る。
     * <b>予約は {@code CONFIRMED} に戻る</b>——キャンセルではないので、経路設計者が
     * もう一度発行できる状態にするだけである。</p>
     */
    @CommandHandler
    public String revertTrackingNumber(RevertTrackingNumberCommand command,
            EventAppender appender, Clock clock) {
        if (bookingStatus != BookingStatus.TRACKING_ISSUED) {
            // 発行していないものは取り消せない。再試行で 2 度届いても 1 度だけ効く。
            throw new IllegalTransition(
                    "状態 " + (bookingStatus == null ? "未受付" : bookingStatus.label())
                            + " の予約の追跡番号は取り消せません");
        }

        appender.append(new TrackingNumberRevertedEvent(command.bookingId(), trackingNumber,
                command.reason(), clock.instant()));
        return command.bookingId();
    }

    @EventSourcingHandler
    void on(TrackingNumberRevertedEvent event) {
        // キャンセルではない。もう一度発行できる状態に戻す。
        this.bookingStatus = BookingStatus.CONFIRMED;
        this.trackingNumber = null;
    }

    /**
     * 通知した経路を経路設計へ戻す（UC08 / US12）。
     *
     * <p>荷主が変更を求めたときに営業が使う。<b>通知したあとだけ開く</b>——通知前に
     * 組み直したいなら、経路設計者が自分で確定し直せばよい。</p>
     *
     * <p><b>{@code RoutingRequestedEvent} を再利用しない</b>（詳細は
     * {@link ReturnToRoutingCommand}）。確定済みの旅程は消さない。</p>
     */
    @CommandHandler
    public String returnToRouting(ReturnToRoutingCommand command, EventAppender appender,
            Clock clock) {
        requireBooked(command.bookingId());
        // 判定は書き直さず述語を呼ぶ（BookingStatus#canReturnToRouting）。
        if (!bookingStatus.canReturnToRouting()) {
            throw new IllegalTransition(
                    "状態 " + bookingStatus.label() + " の予約は経路設計へ戻せません");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            // 経路設計者が何を直せばよいのか分からない。
            throw new BusinessRuleViolation("経路設計へ戻す理由は必須です");
        }

        appender.append(new ReturnedToRoutingEvent(command.bookingId(),
                command.reason().trim(), command.returnedBy(), clock.instant()));
        return command.bookingId();
    }

    @EventSourcingHandler
    void on(ReturnedToRoutingEvent event) {
        this.bookingStatus = BookingStatus.ROUTE_PROPOSED;
        // 経路設計者の手番に戻す。**旅程は消さない**（再設計で入れ替わるまで残す）。
        this.routingStatus = RoutingStatus.ROUTING_REQUESTED;
    }

    @EventSourcingHandler
    void on(ConditionReviewRequestedEvent event) {
        // **状態は動かさない**（ADR-0009 決定 1）。ただし「差し戻されている最中か」は
        // 集約が持つ——営業が返事を返せるのは差し戻されているあいだだけである。
        this.awaitingConditionReviewResponse = true;
    }

    /**
     * 荷主との協議の結果を経路設計者へ返す（UC08 / US10 §受入基準 4 の対）。
     *
     * <p><b>差し戻しは一方向しか無かった。</b> 営業は協議を終えても伝える手段が
     * なく、差し戻しはダッシュボードに出たままだった（IT6 レビュー）。</p>
     *
     * <p><b>状態は動かさない</b>（ADR-0009 決定 1）。条件を実際に直すのは経路設計者で、
     * ここで返すのは協議の結果である。</p>
     */
    @CommandHandler
    public String respondToConditionReview(RespondToConditionReviewCommand command,
            EventAppender appender, Clock clock) {
        requireBooked(command.bookingId());
        if (!awaitingConditionReviewResponse) {
            // 誰も待っていない返事を残さない。二度目もここで断る。
            throw new IllegalTransition("この予約は営業へ差し戻されていません");
        }
        if (command.response() == null || command.response().isBlank()) {
            // 中身が無いと、経路設計者は条件をどう直せばよいのか分からない。
            throw new BusinessRuleViolation("協議の結果は必須です");
        }

        appender.append(new ConditionReviewRespondedEvent(command.bookingId(),
                command.response().trim(), command.respondedBy(), clock.instant()));
        return command.bookingId();
    }

    @EventSourcingHandler
    void on(ConditionReviewRespondedEvent event) {
        // 営業の手番は終わった。**差し戻しの記録は消さない**（投影が両方を持つ）。
        this.awaitingConditionReviewResponse = false;
    }

    @EventSourcingHandler
    void on(RouteSpecificationAdjustedEvent event) {
        this.arrivalDeadline = event.arrivalDeadline();
        // 条件が変われば営業の手番は終わっている（投影も差し戻しの記録を消す）。
        this.awaitingConditionReviewResponse = false;
        // 条件が変わったので、確定済みの経路は設計し直しになる。**旅程は消さない**
        // （再設計で入れ替わるまで残す）。ROUTED のままだと確定し直せない。
        this.routingStatus = RoutingStatus.ROUTING_REQUESTED;
    }

    /**
     * 受け付け済みか。<b>どのコマンドでも最初に通す。</b>
     *
     * <p>受け付けていない予約に操作が届くのは、識別子の打ち間違いか、投影が先に
     * 消えたか。どちらも 409 で断る（500 にすると「壊れた」と読まれる）。</p>
     */
    private void requireBooked(String commandBookingId) {
        if (bookingId == null) {
            throw new IllegalTransition("予約 " + commandBookingId + " は受け付けていません");
        }
    }

    /** いま集約が持っている経路仕様。修正（US32）を反映した値になる。 */
    private RouteSpecification routeSpecification() {
        return new RouteSpecification(origin, destination, arrivalDeadline);
    }

    @EventSourcingHandler
    void on(CargoSpecificationUpdatedEvent event) {
        // 状態は変わらない。仮受付のまま内容だけが差し替わる。
        this.bookingId = event.bookingId();
        this.arrivalDeadline = event.arrivalDeadline();
        this.origin = Location.of(event.originUnLocode());
        this.destination = Location.of(event.destinationUnLocode());
        this.cargoType = event.cargoType();
    }

    @EventSourcingHandler
    void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
        this.bookingStatus = BookingStatus.PRELIMINARY;
        this.routingStatus = RoutingStatus.NOT_ROUTED;
        this.arrivalDeadline = event.arrivalDeadline();
        this.origin = Location.of(event.originUnLocode());
        this.destination = Location.of(event.destinationUnLocode());
        this.cargoType = event.cargoType();
    }

    @EventSourcingHandler
    void on(RoutingRequestedEvent event) {
        this.bookingStatus = BookingStatus.ROUTE_PROPOSED;
        this.routingStatus = RoutingStatus.ROUTING_REQUESTED;
    }

    @EventSourcingHandler
    void on(CargoRoutedEvent event) {
        this.routingStatus = RoutingStatus.ROUTED;
        // 発行のイベントに載せる（US14）。組み直せば新しい旅程で上書きされる。
        this.legs = event.legs();
        // BookingStatus は動かさない。荷主に通知するまでは提案中（US12）。
    }

    /** 復元した予約の状態。画面のボタン出し分けはこの値と述語で決める。 */
    public BookingStatus bookingStatus() {
        return bookingStatus;
    }

    /** 復元した経路設計の進み具合。 */
    public RoutingStatus routingStatus() {
        return routingStatus;
    }
}
