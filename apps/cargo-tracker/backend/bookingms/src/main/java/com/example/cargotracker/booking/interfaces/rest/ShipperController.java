package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.booking.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.booking.domain.model.valueobjects.Email;
import com.example.cargotracker.booking.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ExistsShipperEmailQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.FindShipperQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.FindShippersQuery;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ShipperListView;
import com.example.cargotracker.booking.infrastructure.query.ShipperQueries.ShipperView;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.PendingResponse;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.RegisterShipperRequest;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.RegisterShipperResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 荷主（UC02 / US02）。 */
@RestController
@RequestMapping("/api/v1/booking/shippers")
public class ShipperController {

    private static final long QUERY_TIMEOUT_SECONDS = 5;

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    public ShipperController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping
    public ResponseEntity<RegisterShipperResponse> register(
            @Valid @RequestBody RegisterShipperRequest request) {
        Email email = new Email(request.email());

        // 一意の三段の 1 段目。同時登録のレースでは素通りするので、
        // 2 段目（投影の UNIQUE）と 3 段目（要確認一覧）が本当の砦。
        if (Boolean.TRUE.equals(query(new ExistsShipperEmailQuery(email.value()), Boolean.class))) {
            throw new DuplicateShipperEmailException(email.value());
        }

        String shipperId = UUID.randomUUID().toString();
        ShipperType type = ShipperType.valueOf(request.shipperType());

        // 平文のまま送る。暗号化はイベントのシリアライズ時に行う（ADR-0003 決定 1）。
        // ここで暗号化すると、暗号文が Email の形式検査に落ちる。
        commandGateway.sendAndWait(new RegisterShipperCommand(
                shipperId,
                request.name(),
                type,
                email,
                request.phone(),
                request.address(),
                type == ShipperType.CORPORATE
                        ? new CorporateContract(request.contractNumber(),
                                new DiscountRate(request.discountRate()))
                        : null));

        return ResponseEntity.created(URI.create("/api/v1/booking/shippers/" + shipperId))
                .body(new RegisterShipperResponse(shipperId));
    }

    /**
     * 荷主 1 件。投影がまだなら {@code 202} を返す。
     *
     * <p>{@code 404} にすると「登録に失敗した」と読めてしまう。受け付けたことと
     * 反映が終わったことは別なので、画面が「反映中」を出せるように区別する。</p>
     */
    @GetMapping("/{shipperId}")
    public ResponseEntity<?> find(@PathVariable String shipperId) {
        ShipperView view = query(new FindShipperQuery(shipperId), ShipperView.class);
        if (view == null) {
            return ResponseEntity.accepted()
                    .body(new PendingResponse(shipperId, "登録を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping
    public ShipperListView list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return query(new FindShippersQuery(page, size), ShipperListView.class);
    }

    private <R> R query(Object query, Class<R> responseType) {
        try {
            return queryGateway.query(query, responseType).get(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("問い合わせが中断されました", e);
        } catch (Exception e) {
            throw new IllegalStateException("問い合わせに失敗しました", e);
        }
    }

    /** メールアドレスが既に使われている（409）。 */
    public static class DuplicateShipperEmailException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DuplicateShipperEmailException(String email) {
            super("このメールアドレスは既に登録されています: " + email);
        }
    }
}
