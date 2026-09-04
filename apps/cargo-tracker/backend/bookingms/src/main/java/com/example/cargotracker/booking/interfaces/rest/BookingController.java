package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
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
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindBookingsQuery;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingDtos.BookCargoRequest;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingDtos.BookCargoResponse;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.PendingResponse;
import com.example.cargotracker.shared.domain.location.Location;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 貨物予約（UC03 / US04）。 */
@RestController
@RequestMapping("/api/v1/booking/bookings")
public class BookingController {

    private static final long QUERY_TIMEOUT_SECONDS = 5;

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public BookingController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
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
     * 予約 1 件。投影がまだなら {@code 202} を返す。
     *
     * <p>{@code 404} にすると「登録に失敗した」と読めてしまう。受け付けたことと
     * 反映が終わったことは別なので、画面が「反映中」を出せるように区別する。</p>
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<?> find(@PathVariable String bookingId) {
        BookingView view = query(new FindBookingQuery(bookingId), BookingView.class);
        if (view == null) {
            return ResponseEntity.accepted()
                    .body(new PendingResponse(bookingId, "登録を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping
    public ResponseEntity<BookingListView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "false") boolean includeFinished) {
        return ResponseEntity.ok(
                query(new FindBookingsQuery(page, size, includeFinished), BookingListView.class));
    }

    /** 経路設計の「今日の作業」に出す件数。件数だけでなく、そこから一覧へ行ける。 */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Integer>> summary() {
        return ResponseEntity.ok(Map.of("preliminary",
                query(new CountBookingsByStatusQuery(BookingStatus.PRELIMINARY.name()),
                        Integer.class)));
    }

    /**
     * 種別ごとの付帯情報を組み立てる。
     *
     * <p>空文字は「入力していない」として {@code null} に寄せる。空文字のまま渡すと、
     * 「危険物申告がある」と判断されて集約の検査を素通りする。</p>
     */
    private static CargoSpecification cargoSpecification(BookCargoRequest request) {
        return new CargoSpecification(
                CargoType.valueOf(request.cargoType()),
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T query(Object query, Class<T> type) {
        try {
            return queryGateway.query(query, type).get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("問い合わせが中断されました", e);
        } catch (Exception e) {
            throw new IllegalStateException("問い合わせに失敗しました", e);
        }
    }
}
