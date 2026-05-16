package com.example.cargotracker.bookingms.domain.model.aggregates;

import com.example.cargotracker.bookingms.domain.model.commands.AssignRouteToCargoCommand;
import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.bookingms.domain.model.commands.ConfirmBookingCommand;
import com.example.cargotracker.bookingms.domain.model.commands.HandOffToRoutingCommand;
import com.example.cargotracker.bookingms.domain.model.commands.IssueTrackingNumberCommand;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import com.example.cargotracker.bookingms.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.bookingms.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.bookingms.domain.model.events.CargoHandedOffToRoutingEvent;
import com.example.cargotracker.bookingms.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.bookingms.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.springframework.context.annotation.Profile;

/**
 * 貨物予約 Aggregate（US04）。
 *
 * <p>Axon Framework 5.1 の Event Sourcing パターンで実装する。
 * 詳細は ADR-0007「Axon 5.1 Event Sourcing API」を参照。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>同一 {@code bookingId} で 2 回目の {@code BookCargoCommand} は拒否される（Axon のイベントストアで重複検知）</li>
 *   <li>登録直後は {@code BookingStatus.PRELIMINARY} / {@code RoutingStatus.NOT_ROUTED}</li>
 * </ul>
 */
@EventSourced(idType = String.class, tagKey = "bookingId")
@Profile("!springboot-integration-test")
public final class Cargo {

    private String bookingId;
    private ShipperId shipperId;
    private CargoSpecification cargoSpec;
    private RouteSpecification routeSpec;
    private BookingStatus bookingStatus;
    private RoutingStatus routingStatus;
    private CargoItinerary itinerary;
    private String trackingNumber;

    @EntityCreator
    public Cargo() {
        // Axon が Event 再生で呼び出すデフォルトコンストラクタ。
    }

    /**
     * 貨物予約登録（作成系コマンド）。
     *
     * <p>ADR-0007 推奨パターン: 作成系 Command は {@code static} メソッドとして実装し、
     * {@link EventAppender} を引数で受け取る（{@code AggregateLifecycle.apply()} の代替）。</p>
     */
    @CommandHandler
    public static String book(BookCargoCommand command, EventAppender appender) {
        appender.append(new CargoBookedEvent(
                command.bookingId(),
                command.shipperId(),
                command.cargoSpec(),
                command.routeSpec()));
        return command.bookingId();
    }

    @EventSourcingHandler
    public void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
        this.shipperId = event.shipperId();
        this.cargoSpec = event.cargoSpec();
        this.routeSpec = event.routeSpec();
        this.bookingStatus = BookingStatus.PRELIMINARY;
        this.routingStatus = RoutingStatus.NOT_ROUTED;
    }

    /**
     * 予約引き渡し（US06 / UC04）。
     *
     * <p>仮受付状態の予約を経路設計者に引き渡す。受け付けるのは {@code PRELIMINARY}
     * 状態のみ。既に経路設計中・確定済み・キャンセル等の状態では拒否する。</p>
     */
    @CommandHandler
    public void handOffToRouting(HandOffToRoutingCommand command, EventAppender appender) {
        if (bookingStatus != BookingStatus.PRELIMINARY) {
            throw new IllegalStateException(
                    "PRELIMINARY 状態の予約のみ経路設計に引き渡せます。現状態: " + bookingStatus);
        }
        appender.append(new CargoHandedOffToRoutingEvent(command.bookingId()));
    }

    @EventSourcingHandler
    public void on(CargoHandedOffToRoutingEvent event) {
        this.bookingStatus = BookingStatus.ROUTING;
    }

    /**
     * 経路紐付け（US11 / UC09）。
     *
     * <p>ROUTING 状態の予約に確定経路を紐付け、ROUTE_PROPOSED に遷移する。</p>
     */
    @CommandHandler
    public void assignRoute(AssignRouteToCargoCommand command, EventAppender appender) {
        if (bookingStatus != BookingStatus.ROUTING) {
            throw new IllegalStateException(
                    "ROUTING 状態の予約のみ経路を紐付けできます。現状態: " + bookingStatus);
        }
        appender.append(new CargoRoutedEvent(command.bookingId(), command.itinerary()));
    }

    @EventSourcingHandler
    public void on(CargoRoutedEvent event) {
        this.bookingStatus = BookingStatus.ROUTE_PROPOSED;
        this.itinerary = event.itinerary();
    }

    /**
     * 予約確定（US13 / UC11）。
     *
     * <p>ROUTE_PROPOSED 状態の予約を確定する。</p>
     */
    @CommandHandler
    public void confirmBooking(ConfirmBookingCommand command, EventAppender appender) {
        if (bookingStatus != BookingStatus.ROUTE_PROPOSED) {
            throw new IllegalStateException(
                    "ROUTE_PROPOSED 状態の予約のみ確定できます。現状態: " + bookingStatus);
        }
        appender.append(new BookingConfirmedEvent(command.bookingId()));
    }

    @EventSourcingHandler
    public void on(BookingConfirmedEvent event) {
        this.bookingStatus = BookingStatus.CONFIRMED;
    }

    /**
     * 追跡番号発行（US14 / UC12）。
     *
     * <p>CONFIRMED 状態の予約に追跡番号を発行する。形式: TRK-{YYYYMMDD}-{UUID前8桁大文字}</p>
     */
    @CommandHandler
    public void issueTrackingNumber(IssueTrackingNumberCommand command, EventAppender appender) {
        if (bookingStatus != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "CONFIRMED 状態の予約のみ追跡番号を発行できます。現状態: " + bookingStatus);
        }
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String tracking = "TRK-" + date + "-" + suffix;
        appender.append(new TrackingNumberIssuedEvent(command.bookingId(), tracking));
    }

    @EventSourcingHandler
    public void on(TrackingNumberIssuedEvent event) {
        this.bookingStatus = BookingStatus.TRACKING_ISSUED;
        this.trackingNumber = event.trackingNumber();
    }

    public String getBookingId() {
        return bookingId;
    }

    public ShipperId getShipperId() {
        return shipperId;
    }

    public CargoSpecification getCargoSpec() {
        return cargoSpec;
    }

    public RouteSpecification getRouteSpec() {
        return routeSpec;
    }

    public BookingStatus getBookingStatus() {
        return bookingStatus;
    }

    public RoutingStatus getRoutingStatus() {
        return routingStatus;
    }

    public CargoItinerary getItinerary() {
        return itinerary;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
