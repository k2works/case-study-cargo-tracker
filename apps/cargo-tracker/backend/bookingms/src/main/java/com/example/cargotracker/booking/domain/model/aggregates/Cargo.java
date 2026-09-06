package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestRoutingCommand;
import com.example.cargotracker.booking.domain.model.commands.UpdateCargoSpecificationCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.commands.AssignRouteCommand;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.time.Clock;
import java.time.LocalDate;
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
 * <p>不変条件（domain-model.md「Cargo 集約の不変条件」）:</p>
 * <ul>
 *   <li>1: BookingId は不変。ShipperId は登録時に必須</li>
 *   <li>2: 出発地と目的地は異なる（RouteSpecification が守る）</li>
 *   <li>3: 危険物は申告、冷凍・冷蔵は温度条件が必須（CargoSpecification が守る）</li>
 * </ul>
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

    @EntityCreator
    public Cargo() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 予約を受け付ける。
     *
     * <p><b>static ではなくインスタンスのハンドラにしている。</b> static（作る側）と
     * インスタンス（既にある側）を両方置くと、集約が既に存在しても static のほうが
     * 呼ばれ、2 度目の受付が通ってしまう（IT2 で実測）。{@code @EntityCreator} が
     * 空の集約を用意するので、インスタンス側だけで両方を扱える。</p>
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
        validate(command, LocalDate.now(clock));
        CargoSpecification spec = command.cargoSpecification();
        appender.append(new CargoBookedEvent(
                command.bookingId(),
                command.shipperId(),
                command.routeSpecification().origin().unLocode().value(),
                command.routeSpecification().destination().unLocode().value(),
                command.routeSpecification().arrivalDeadline(),
                spec.cargoType().name(),
                spec.weight().kilograms(),
                spec.dimensions().lengthCm(),
                spec.dimensions().widthCm(),
                spec.dimensions().heightCm(),
                spec.quantity(),
                spec.productName(),
                spec.hazardousDeclaration() == null ? null : spec.hazardousDeclaration().imoClass(),
                spec.hazardousDeclaration() == null ? null : spec.hazardousDeclaration().unNumber(),
                spec.temperatureRequirement() == null
                        ? null : spec.temperatureRequirement().minCelsius(),
                spec.temperatureRequirement() == null
                        ? null : spec.temperatureRequirement().maxCelsius(),
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
        if (bookingId == null) {
            throw new IllegalTransition("予約 " + command.bookingId() + " は受け付けていません");
        }
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
     * ここで {@code if (status == PRELIMINARY)} と書くと、状態が増えたときに集約と
     * 遷移表の判断が食い違う。</p>
     *
     * <p><b>登録と同じ検査を通す。</b> 修正用に検査を書き直すと「登録では断るのに
     * 修正では通る」が生まれる。危険物の申告も冷凍の温度条件も
     * {@code CargoSpecification} が同じように守る。</p>
     */
    @CommandHandler
    public String updateSpecification(UpdateCargoSpecificationCommand command,
            EventAppender appender, Clock clock) {
        if (bookingId == null) {
            throw new IllegalTransition("予約 " + command.bookingId() + " は受け付けていません");
        }
        if (!bookingStatus.canUpdateSpecification()) {
            throw new IllegalTransition("状態 " + bookingStatus.label() + " の予約は修正できません");
        }
        // **期限は「変えたときだけ」検査する。** 据え置きにも今日以降を求めると、
        // 期限を過ぎた仮受付の予約は品名すら直せなくなる（誤りに気づくのは
        // たいてい期限が近づいてからで、そのときには直せない）。
        validate(command.cargoSpecification(), command.routeSpecification(),
                LocalDate.now(clock), arrivalDeadline);

        CargoSpecification spec = command.cargoSpecification();
        appender.append(new CargoSpecificationUpdatedEvent(
                command.bookingId(),
                command.routeSpecification().origin().unLocode().value(),
                command.routeSpecification().destination().unLocode().value(),
                command.routeSpecification().arrivalDeadline(),
                spec.cargoType().name(),
                spec.weight().kilograms(),
                spec.dimensions().lengthCm(),
                spec.dimensions().widthCm(),
                spec.dimensions().heightCm(),
                spec.quantity(),
                spec.productName(),
                spec.hazardousDeclaration() == null ? null : spec.hazardousDeclaration().imoClass(),
                spec.hazardousDeclaration() == null ? null : spec.hazardousDeclaration().unNumber(),
                spec.temperatureRequirement() == null
                        ? null : spec.temperatureRequirement().minCelsius(),
                spec.temperatureRequirement() == null
                        ? null : spec.temperatureRequirement().maxCelsius(),
                command.updatedBy(),
                clock.instant()));
        return command.bookingId();
    }

    /**
     * 選んだ経路を確定する（UC07 / US09）。
     *
     * <p><b>旅程が経路仕様を満たすかは集約が見る</b>（不変条件 5）。画面は候補を
     * そのまま送るだけで、「候補は探索が作ったのだから正しい」としない。探索と集約は
     * 別の判断であり、API を直接叩く経路もある。</p>
     *
     * <p>区間の連結と時刻の昇順は {@link CargoItinerary} が守る（不変条件 4）。</p>
     *
     * <p><b>{@code BookingStatus} は動かさない。</b> 荷主に通知するまでは提案中である。</p>
     */
    @CommandHandler
    public String assignRoute(AssignRouteCommand command, EventAppender appender, Clock clock) {
        if (bookingId == null) {
            throw new IllegalTransition("予約 " + command.bookingId() + " は受け付けていません");
        }
        if (routingStatus != RoutingStatus.ROUTING_REQUESTED
                && routingStatus != RoutingStatus.MISROUTED) {
            // 引き渡していない予約に経路が付くと、営業の知らないところで設計が進む。
            // 誤配（MISROUTED）からの再設計は許す（US28）。
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

        appender.append(new CargoRoutedEvent(
                command.bookingId(),
                command.itinerary().legs().stream()
                        .map(leg -> new CargoRoutedEvent.Leg(
                                leg.voyageNumber(),
                                leg.load().unLocode().value(),
                                leg.unload().unLocode().value(),
                                leg.loadTime(),
                                leg.unloadTime()))
                        .toList(),
                command.assignedBy(),
                clock.instant()));
        return command.bookingId();
    }

    /** いま集約が持っている経路仕様。修正（US32）を反映した値になる。 */
    private RouteSpecification routeSpecification() {
        return new RouteSpecification(origin, destination, arrivalDeadline);
    }

    private static void validate(BookCargoCommand command, LocalDate today) {
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            throw new BusinessRuleViolation("予約 ID は必須です");
        }
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            // 荷主の分からない予約は、通知も請求も宛先が無い。
            throw new BusinessRuleViolation("荷主 ID は必須です");
        }
        validate(command.cargoSpecification(), command.routeSpecification(), today, null);
    }

    /**
     * 受付と修正で同じ検査を通す。分けて書くと片方だけが古くなる。
     *
     * <p>{@code currentDeadline} は据え置きを見分けるためのもの（受付では null）。
     * 期限を動かさない修正は、その期限が過去でも通す。</p>
     */
    private static void validate(CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification, LocalDate today, LocalDate currentDeadline) {
        if (cargoSpecification == null) {
            throw new BusinessRuleViolation("貨物仕様は必須です");
        }
        if (routeSpecification == null) {
            throw new BusinessRuleViolation("輸送条件は必須です");
        }
        // 期限は日付で比較する。当日着は間に合う扱い（不変条件 5）。
        //
        // **新規の受け付けでだけ検査する。** 復元（@EventSourcingHandler）では見ない。
        // 見ると、受け付けたあとに期限を過ぎた予約が読めなくなる。
        if (routeSpecification.arrivalDeadline().equals(currentDeadline)) {
            return;
        }
        if (routeSpecification.arrivalDeadline().isBefore(today)) {
            throw new BusinessRuleViolation(
                    "到着期限が過去の日付です: " + routeSpecification.arrivalDeadline());
        }
    }

    @EventSourcingHandler
    void on(CargoSpecificationUpdatedEvent event) {
        // 状態は変わらない。仮受付のまま内容だけが差し替わる。
        this.bookingId = event.bookingId();
        this.arrivalDeadline = event.arrivalDeadline();
        this.origin = Location.of(event.originUnLocode());
        this.destination = Location.of(event.destinationUnLocode());
    }

    @EventSourcingHandler
    void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
        this.bookingStatus = BookingStatus.PRELIMINARY;
        this.routingStatus = RoutingStatus.NOT_ROUTED;
        this.arrivalDeadline = event.arrivalDeadline();
        this.origin = Location.of(event.originUnLocode());
        this.destination = Location.of(event.destinationUnLocode());
    }

    @EventSourcingHandler
    void on(RoutingRequestedEvent event) {
        this.bookingStatus = BookingStatus.ROUTE_PROPOSED;
        this.routingStatus = RoutingStatus.ROUTING_REQUESTED;
    }

    @EventSourcingHandler
    void on(CargoRoutedEvent event) {
        this.routingStatus = RoutingStatus.ROUTED;
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
