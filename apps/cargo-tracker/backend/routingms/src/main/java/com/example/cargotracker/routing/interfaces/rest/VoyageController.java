package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyageQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyagesQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageListView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageView;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.MovementRequest;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.PendingResponse;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.RegisterVoyageRequest;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.RegisterVoyageResponse;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

/** 航海スケジュール（UC19 / US24）。 */
@RestController
@RequestMapping("/api/v1/routing/voyages")
public class VoyageController {

    private static final long QUERY_TIMEOUT_SECONDS = 5;

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public VoyageController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping
    public ResponseEntity<RegisterVoyageResponse> register(
            @Valid @RequestBody RegisterVoyageRequest request,
            @RequestHeader(name = "X-Auth-Username", required = false) String username) {
        commandGateway.sendAndWait(new RegisterVoyageCommand(
                request.voyageNumber(),
                new Carrier(request.carrierCode(), request.carrierName()),
                new VesselName(request.vesselName()),
                new Schedule(request.movements().stream()
                        .map(VoyageController::toMovement)
                        .toList()),
                acceptedCargoTypes(request.acceptedCargoTypes()),
                username));

        return ResponseEntity
                .created(URI.create("/api/v1/routing/voyages/" + request.voyageNumber()))
                .body(new RegisterVoyageResponse(request.voyageNumber()));
    }

    /**
     * 航海 1 件。投影がまだなら {@code 202} を返す。
     *
     * <p>{@code 404} にすると「登録に失敗した」と読めてしまう。受け付けたことと反映が
     * 終わったことは別なので、画面が「反映中」を出せるように区別する。</p>
     */
    @GetMapping("/{voyageNumber}")
    public ResponseEntity<?> find(@PathVariable String voyageNumber) {
        VoyageView view = query(new FindVoyageQuery(voyageNumber), VoyageView.class);
        if (view == null) {
            return ResponseEntity.accepted().body(new PendingResponse(
                    voyageNumber, "登録を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping
    public ResponseEntity<VoyageListView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "false") boolean includeFinished,
            @RequestParam(required = false) String cargoType) {
        return ResponseEntity.ok(query(
                new FindVoyagesQuery(page, size, includeFinished, normalize(cargoType)),
                VoyageListView.class));
    }

    private static CarrierMovement toMovement(MovementRequest request) {
        return new CarrierMovement(
                Location.of(request.departureUnLocode()),
                Location.of(request.arrivalUnLocode()),
                request.departureAt(),
                request.arrivalAt());
    }

    /**
     * 空文字は「絞り込まない」に寄せる。
     *
     * <p>空文字のまま渡すと、どの航海の {@code voyage_accepted_cargo_type} にも
     * 一致せず一覧が黙って空になる。</p>
     */
    private static String normalize(String cargoType) {
        if (cargoType == null || cargoType.isBlank()) {
            return null;
        }
        try {
            return CargoType.valueOf(cargoType).name();
        } catch (IllegalArgumentException e) {
            // 知らない種別で絞ると 0 件になる。0 件は「無い」と読めるので、
            // 入力が誤っていることを業務規則違反として返す。
            throw new BusinessRuleViolation("知らない貨物種別です: " + cargoType);
        }
    }

    /** 未選択は集約が一般貨物のみに決める（不変条件 4）。ここでは変換だけする。 */
    private static Set<CargoType> acceptedCargoTypes(List<String> names) {
        if (names == null) {
            return Set.of();
        }
        Set<CargoType> types = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                types.add(CargoType.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleViolation("知らない貨物種別です: " + name);
            }
        }
        return types;
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
