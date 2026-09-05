package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.domain.model.commands.CancelVoyageCommand;
import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.commands.UpdateVoyageScheduleCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageSearchCriteria;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyageQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyagesQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageListView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageView;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.CancelVoyageRequest;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.MovementRequest;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.PendingResponse;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.RegisterVoyageRequest;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.RegisterVoyageResponse;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.UpdateVoyageRequest;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageDtos.VoyageDiffResponse;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

/** 航海スケジュール（UC19 / US24）。 */
@RestController
@RequestMapping("/api/v1/routing/voyages")
public class VoyageController {

    private final CommandGateway commandGateway;
    private final QueryDispatcher queries;

    public VoyageController(CommandGateway commandGateway, QueryDispatcher queries) {
        this.commandGateway = commandGateway;
        this.queries = queries;
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
     * スケジュールを更新する（US25）。
     *
     * <p>更新できる条件（登録済みか・キャンセル済みでないか）は集約が見る。ここで
     * 先に問い合わせて分岐すると、同じ判断が 2 か所になって片方が古くなる。</p>
     */
    @PutMapping("/{voyageNumber}")
    public ResponseEntity<RegisterVoyageResponse> update(
            @PathVariable String voyageNumber,
            @Valid @RequestBody UpdateVoyageRequest request,
            @RequestHeader(name = "X-Auth-Username", required = false) String username) {
        commandGateway.sendAndWait(new UpdateVoyageScheduleCommand(
                voyageNumber,
                new Carrier(request.carrierCode(), request.carrierName()),
                new VesselName(request.vesselName()),
                new Schedule(request.movements().stream()
                        .map(VoyageController::toMovement)
                        .toList()),
                acceptedCargoTypes(request.acceptedCargoTypes()),
                username));

        return ResponseEntity.ok(new RegisterVoyageResponse(voyageNumber));
    }

    /**
     * 航海をキャンセルする（US24 / IT5 R.1）。
     *
     * <p>止めてよいか（登録済みか・既に止まっていないか）は集約が見る。ここで
     * 先に問い合わせて分岐すると、同じ判断が 2 か所になって片方が古くなる。</p>
     */
    @PostMapping("/{voyageNumber}/cancel")
    public ResponseEntity<RegisterVoyageResponse> cancel(
            @PathVariable String voyageNumber,
            @Valid @RequestBody CancelVoyageRequest request,
            @RequestHeader(name = "X-Auth-Username", required = false) String username) {
        commandGateway.sendAndWait(
                new CancelVoyageCommand(voyageNumber, request.reason(), username));

        return ResponseEntity.ok(new RegisterVoyageResponse(voyageNumber));
    }

    /**
     * 更新前後の差分（US25 §受入基準 2）。
     *
     * <p><b>サーバが出す。</b> 画面で 2 つの値を並べて {@code if} を積み上げると、
     * 航海に属性が増えるたびに比べ忘れが生まれる。</p>
     *
     * <p>問い合わせなので副作用は無い。POST にしているのは、比べる相手（更新内容）を
     * 本文で送るためである。</p>
     */
    @PostMapping("/{voyageNumber}/diff")
    public ResponseEntity<?> diff(@PathVariable String voyageNumber,
            @Valid @RequestBody UpdateVoyageRequest request) {
        VoyageView stored = queries.query(new FindVoyageQuery(voyageNumber), VoyageView.class);
        if (stored == null) {
            // 比べる相手が無い。404 にすると「登録に失敗した」と読める。
            return ResponseEntity.accepted().body(new PendingResponse(
                    voyageNumber, "登録を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(new VoyageDiffResponse(voyageNumber,
                VoyageScheduleDiff.between(snapshotOf(stored), snapshotOf(request))));
    }

    private static VoyageScheduleDiff.VoyageSnapshot snapshotOf(VoyageView view) {
        return new VoyageScheduleDiff.VoyageSnapshot(
                view.carrierCode(), view.carrierName(), view.vesselName(),
                view.acceptedCargoTypes(),
                view.movements().stream()
                        .map(m -> new VoyageScheduleDiff.VoyageSnapshot.Movement(
                                m.departureUnLocode(), m.arrivalUnLocode(),
                                m.departureAt(), m.arrivalAt()))
                        .toList());
    }

    private static VoyageScheduleDiff.VoyageSnapshot snapshotOf(UpdateVoyageRequest request) {
        // 入力も集約と同じ既定（空なら一般貨物のみ）に寄せてから比べる。
        // 寄せずに比べると、何も選ばなかっただけで「対応貨物種別が変わった」と出る。
        return new VoyageScheduleDiff.VoyageSnapshot(
                request.carrierCode(), request.carrierName(), request.vesselName(),
                CargoType.resolveAcceptedNames(acceptedCargoTypes(
                        request.acceptedCargoTypes())),
                request.movements().stream()
                        .map(m -> new VoyageScheduleDiff.VoyageSnapshot.Movement(
                                m.departureUnLocode(), m.arrivalUnLocode(),
                                m.departureAt(), m.arrivalAt()))
                        .toList());
    }

    /**
     * 航海 1 件。投影がまだなら {@code 202} を返す。
     *
     * <p>{@code 404} にすると「登録に失敗した」と読めてしまう。受け付けたことと反映が
     * 終わったことは別なので、画面が「反映中」を出せるように区別する。</p>
     */
    @GetMapping("/{voyageNumber}")
    public ResponseEntity<?> find(@PathVariable String voyageNumber) {
        VoyageView view = queries.query(new FindVoyageQuery(voyageNumber), VoyageView.class);
        if (view == null) {
            return ResponseEntity.accepted().body(new PendingResponse(
                    voyageNumber, "登録を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(view);
    }

    /**
     * 一覧と検索（S32 / US07）。
     *
     * <p>条件の解釈（空文字は指定なし・知らない種別は断る）は
     * {@link VoyageSearchCriteria} が持つ。ここで判断すると、同じ規則が画面・
     * Controller・クエリの 3 か所に散る。</p>
     */
    @GetMapping
    public ResponseEntity<VoyageListView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "false") boolean includeFinished,
            @RequestParam(required = false) String departure,
            @RequestParam(required = false) String arrival,
            @RequestParam(required = false) Instant departFrom,
            @RequestParam(required = false) Instant departTo,
            @RequestParam(required = false) String cargoType) {
        return ResponseEntity.ok(queries.query(
                new FindVoyagesQuery(page, size, includeFinished,
                        VoyageSearchCriteria.of(departure, arrival, departFrom, departTo,
                                cargoType)),
                VoyageListView.class));
    }

    private static CarrierMovement toMovement(MovementRequest request) {
        return new CarrierMovement(
                Location.of(request.departureUnLocode()),
                Location.of(request.arrivalUnLocode()),
                request.departureAt(),
                request.arrivalAt());
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

}
