package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestRoutingCommand;
import com.example.cargotracker.booking.domain.model.commands.UpdateCargoSpecificationCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
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
        if (!bookingStatus.canTransitionTo(BookingStatus.ROUTE_PROPOSED)) {
            throw new IllegalTransition(
                    "状態 " + bookingStatus + " の予約は経路設計へ引き渡せません");
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
            throw new IllegalTransition("状態 " + bookingStatus + " の予約は修正できません");
        }
        validate(command.cargoSpecification(), command.routeSpecification(),
                LocalDate.now(clock));

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
                command.updatedBy()));
        return command.bookingId();
    }

    private static void validate(BookCargoCommand command, LocalDate today) {
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            throw new BusinessRuleViolation("予約 ID は必須です");
        }
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            // 荷主の分からない予約は、通知も請求も宛先が無い。
            throw new BusinessRuleViolation("荷主 ID は必須です");
        }
        validate(command.cargoSpecification(), command.routeSpecification(), today);
    }

    /** 受付と修正で同じ検査を通す。分けて書くと片方だけが古くなる。 */
    private static void validate(CargoSpecification cargoSpecification,
            RouteSpecification routeSpecification, LocalDate today) {
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
        if (routeSpecification.arrivalDeadline().isBefore(today)) {
            throw new BusinessRuleViolation(
                    "到着期限が過去の日付です: " + routeSpecification.arrivalDeadline());
        }
    }

    @EventSourcingHandler
    void on(CargoSpecificationUpdatedEvent event) {
        // 状態は変わらない。仮受付のまま内容だけが差し替わる。
        this.bookingId = event.bookingId();
    }

    @EventSourcingHandler
    void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
        this.bookingStatus = BookingStatus.PRELIMINARY;
        this.routingStatus = RoutingStatus.NOT_ROUTED;
    }

    @EventSourcingHandler
    void on(RoutingRequestedEvent event) {
        this.bookingStatus = BookingStatus.ROUTE_PROPOSED;
        this.routingStatus = RoutingStatus.ROUTING_REQUESTED;
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
