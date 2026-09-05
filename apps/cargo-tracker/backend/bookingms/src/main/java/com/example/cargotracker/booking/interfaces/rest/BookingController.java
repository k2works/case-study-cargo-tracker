package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestRoutingCommand;
import com.example.cargotracker.booking.domain.model.commands.UpdateCargoSpecificationCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.BookingView;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.CountBookingsByStatusQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingRevisionsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.RevisionListView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindRoutingWorklistQuery;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingDtos;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingDtos.BookCargoRequest;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingDtos.BookCargoResponse;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.PendingResponse;
import com.example.cargotracker.shared.domain.location.Location;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 貨物予約（UC03 / US04）。 */
@RestController
@RequestMapping("/api/v1/booking/bookings")
public class BookingController {


    private final CommandGateway commandGateway;
    private final QueryDispatcher queries;

    public BookingController(CommandGateway commandGateway, QueryDispatcher queries) {
        this.commandGateway = commandGateway;
        this.queries = queries;
    }

    @PostMapping
    public ResponseEntity<BookCargoResponse> book(@Valid @RequestBody BookCargoRequest request,
            @RequestHeader(name = "X-Auth-Username", required = false) String username) {
        String bookingId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(new BookCargoCommand(
                bookingId,
                request.shipperId(),
                cargoSpecification(request),
                new RouteSpecification(
                        Location.of(request.originUnLocode()),
                        Location.of(request.destinationUnLocode()),
                        request.arrivalDeadline()),
                username));

        return ResponseEntity.created(URI.create("/api/v1/booking/bookings/" + bookingId))
                .body(new BookCargoResponse(bookingId));
    }

    /**
     * 仮受付の予約情報を修正する（US32）。
     *
     * <p>修正できる状態かは集約が遷移表の述語で判断する。ここで先に問い合わせて
     * 分岐すると、同じ判断が 2 か所になって片方が古くなる。</p>
     */
    @PutMapping("/{bookingId}")
    public ResponseEntity<BookCargoResponse> update(@PathVariable String bookingId,
            @Valid @RequestBody BookingDtos.UpdateBookingRequest request,
            @RequestHeader(name = "X-Auth-Username", required = false) String username) {
        commandGateway.sendAndWait(new UpdateCargoSpecificationCommand(
                bookingId,
                cargoSpecification(request),
                new RouteSpecification(
                        Location.of(request.originUnLocode()),
                        Location.of(request.destinationUnLocode()),
                        request.arrivalDeadline()),
                username));

        return ResponseEntity.ok(new BookCargoResponse(bookingId));
    }

    /**
     * 予約 1 件。投影がまだなら {@code 202} を返す。
     *
     * <p>{@code 404} にすると「登録に失敗した」と読めてしまう。受け付けたことと
     * 反映が終わったことは別なので、画面が「反映中」を出せるように区別する。</p>
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<?> find(@PathVariable String bookingId) {
        BookingView view = queries.query(new FindBookingQuery(bookingId), BookingView.class);
        if (view == null) {
            return ResponseEntity.accepted()
                    .body(new PendingResponse(bookingId, "登録を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(view);
    }

    /**
     * 修正履歴（US32 §受入基準 4「何を変えたか」）。
     *
     * <p>一度も直していなければ空の一覧を返す。{@code 404} にすると「予約が無い」と
     * 読める。</p>
     *
     * <p>読む相手は予約詳細と同じ（営業・経路設計・追跡）なので、認可の宣言は
     * {@code /bookings/**} がそのまま当たる。絞る必要が出たら宣言を先に置く。</p>
     */
    @GetMapping("/{bookingId}/revisions")
    public ResponseEntity<RevisionListView> revisions(@PathVariable String bookingId) {
        return ResponseEntity.ok(queries.query(
                new FindBookingRevisionsQuery(bookingId), RevisionListView.class));
    }

    @GetMapping
    public ResponseEntity<BookingListView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "false") boolean includeFinished) {
        return ResponseEntity.ok(
                queries.query(new FindBookingsQuery(page, size, includeFinished), BookingListView.class));
    }

    /**
     * 経路設計者に引き渡す（US06）。
     *
     * <p>遷移できるかは集約が {@code BookingStatus} の述語で判断する。ここでは
     * 判定を書き直さない。書き直すと、片方だけ直したときに画面と集約の判断が
     * 食い違う。</p>
     */
    @PostMapping("/{bookingId}/routing-request")
    public ResponseEntity<BookCargoResponse> requestRouting(@PathVariable String bookingId,
            @RequestHeader(name = "X-Auth-Username", required = false) String username) {
        commandGateway.sendAndWait(new RequestRoutingCommand(bookingId, username));
        return ResponseEntity.accepted().body(new BookCargoResponse(bookingId));
    }

    /**
     * 経路設計作業一覧（S30）。
     *
     * <p>routingms ではなくここに置く。{@code routing_read_db} に予約の表は無く、
     * 一覧のために写しも作らない（写しを作ると Booking の状態と二重管理になる）。
     * 経路設計ロールへの開放は Gateway のルートとロールで行う。</p>
     */
    @GetMapping("/routing-worklist")
    public ResponseEntity<BookingListView> routingWorklist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "false") boolean includeRouted) {
        return ResponseEntity.ok(queries.query(
                new FindRoutingWorklistQuery(page, size, includeRouted), BookingListView.class));
    }

    /**
     * ダッシュボード（S02）の「今日の作業」の件数。
     *
     * <p>ロールごとに見るものが違う。{@code preliminary}（まだ引き渡していない予約）は
     * <b>営業の仕事</b>で、{@code routingWorklist}（設計待ち・誤配）は経路設計者の仕事。
     * 経路設計者に {@code preliminary} を出しても、その件数に対して打てる手が無い。</p>
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Integer>> summary() {
        BookingListView worklist = queries.query(new FindRoutingWorklistQuery(0, 1, false),
                BookingListView.class);
        return ResponseEntity.ok(Map.of(
                "preliminary",
                queries.query(new CountBookingsByStatusQuery(BookingStatus.PRELIMINARY.name()),
                        Integer.class),
                "routingWorklist", worklist.total()));
    }

    /**
     * 種別ごとの付帯情報を組み立てる。
     *
     * <p>空文字は「入力していない」として {@code null} に寄せる。空文字のまま渡すと、
     * 「危険物申告がある」と判断されて集約の検査を素通りする。</p>
     */
    private static CargoSpecification cargoSpecification(BookingDtos.CargoFields request) {
        return new CargoSpecification(
                cargoType(request.cargoType()),
                new Weight(request.weightKg()),
                new Dimensions(request.lengthCm(), request.widthCm(), request.heightCm()),
                request.quantity(),
                request.productName(),
                blank(request.hazardImoClass()) && blank(request.hazardUnNumber())
                        ? null
                        : new HazardousDeclaration(request.hazardImoClass(),
                                request.hazardUnNumber()),
                request.temperatureMinC() == null && request.temperatureMaxC() == null
                        ? null
                        : new TemperatureRequirement(request.temperatureMinC(),
                                request.temperatureMaxC()));
    }

    /**
     * 知らない貨物種別を素の例外にしない。
     *
     * <p>{@code CargoType.valueOf} の {@code IllegalArgumentException} は
     * {@code ApiExceptionHandler} の対象外なので 500 に化ける。入力の誤りは
     * 業務規則違反として 422 で返す。</p>
     */
    private static CargoType cargoType(String name) {
        try {
            return CargoType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolation("知らない貨物種別です: " + name);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
