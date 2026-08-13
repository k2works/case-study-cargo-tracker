package com.example.cargotracker.demo;

import static com.example.cargotracker.demo.DemoActors.ACTOR;
import static com.example.cargotracker.demo.DemoActors.require;

import com.example.cargotracker.booking.application.internal.commandservices.AssignToRoutingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ConfirmBookingCommandService;
import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.Quantity;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.routing.application.internal.commandservices.ProposeRoutesCommandService;
import com.example.cargotracker.routing.application.internal.commandservices.SelectRouteCommandService;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.valueobjects.RelaxationRequest;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingBookingId;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices
        .RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 予約を登録し、経路を割り当てて確定するまで（マニュアル 03 / 04 / 05）。
 *
 * <p><strong>画面と同じ順番で呼ぶ。</strong> 途中の状態を直接作ると、
 * 画面が実際に到達しうる状態かを確かめないまま図を作ることになる。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoBookingSteps {

    private final RegisterShipperCommandService registerShipper;
    private final BookCargoCommandService book;
    private final AssignToRoutingCommandService assign;
    private final ProposeRoutesCommandService propose;
    private final SelectRouteCommandService select;
    private final ConfirmBookingCommandService confirm;
    private final Clock clock;

    DemoBookingSteps(
            RegisterShipperCommandService registerShipper,
            BookCargoCommandService book,
            AssignToRoutingCommandService assign,
            ProposeRoutesCommandService propose,
            SelectRouteCommandService select,
            ConfirmBookingCommandService confirm,
            Clock clock) {
        this.registerShipper = registerShipper;
        this.book = book;
        this.assign = assign;
        this.propose = propose;
        this.select = select;
        this.confirm = confirm;
        this.clock = clock;
    }

    /** 予約を登録し、経路を割り当てて確定する。 */
    BookingId confirmed(
            String origin, String destination, CargoType type, String weight, int days) {
        BookingId id = booked(origin, destination, type, weight, days);
        assign.assign(id, ACTOR);
        propose.propose(new RoutingBookingId(id.value()), RelaxationRequest.none(), ACTOR)
                .flatMap(proposal -> proposal.candidates().stream()
                        .filter(ProposedRoute::selectable)
                        .findFirst())
                .ifPresent(route -> select.select(
                        new RoutingBookingId(id.value()), route.voyageNumber(), ACTOR));
        var confirmed = confirm.confirm(id, ACTOR);
        require(confirmed.isConfirmed(), "予約を確定できませんでした: " + confirmed.reason());
        return id;
    }

    private BookingId booked(
            String origin, String destination, CargoType type, String weight, int days) {
        var result = book.book(new BookCargoCommand(
                demoShipper(),
                new CargoSpecification(
                        type, Weight.ofKilograms(new BigDecimal(weight)),
                        Dimensions.ofNullableCentimeters(null, null, null),
                        Quantity.ofNullable(null), Description.ofNullable(DemoInstallMarker.MARKER_DESCRIPTION),
                        null, null),
                new RouteSpecification(
                        Location.of(origin), Location.of(destination),
                        LocalDate.now(clock).plusDays(days))),
                ACTOR);
        require(result.isBooked(), "予約を登録できませんでした");
        return result.bookingId();
    }

    /**
     * 動作確認用の荷主を用意する。
     *
     * <p><strong>{@code db/demo} の SQL に依存しない。</strong> あの SQL は local / dev の
     * Flyway locations に入れたときだけ適用される。ここで前提にすると、
     * <strong>検査は緑なのに起動の仕方によっては何も作られない</strong>形になる。
     * 既にいれば作らない（登録サービスが連絡先で重複を判定する）。
     */
    private ShipperId demoShipper() {
        var result = registerShipper.register(
                new ShipperName("山田商事"),
                new Email("shipper-sample@example.com"),
                new Phone("03-0000-0000"),
                new Address("JP", "100-0001", "東京都", "千代田区", "千代田 1-1 サンプルビル 5F"));
        return new ShipperId(result.shipper().id().value());
    }
}
