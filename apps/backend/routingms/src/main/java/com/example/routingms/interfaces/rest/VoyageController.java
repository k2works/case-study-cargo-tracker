package com.example.routingms.interfaces.rest;

import com.example.routingms.application.internal.RegisterVoyageCommand;
import com.example.routingms.application.internal.RegisterVoyageUseCase;
import com.example.routingms.application.internal.VoyageOutcome;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.routingms.application.port.LocationRepository;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.shared.domain.model.Location;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/voyages")
public class VoyageController {

    private final RegisterVoyageUseCase registerVoyage;
    private final LocationRepository locations;

    public VoyageController(RegisterVoyageUseCase registerVoyage, LocationRepository locations) {
        this.registerVoyage = registerVoyage;
        this.locations = locations;
    }

    /**
     * 航海スケジュールを登録する（US24）。
     *
     * <p>同じ航海番号が既にあるときは 409 を返すが、これは失敗ではなく問いかけである。
     * 差分を添えて返し、画面は「上書きする」「やめる」を選ばせる。
     */
    @PostMapping
    public ResponseEntity<?> register(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @Valid @RequestBody VoyageRequest request) {
        requireRoutingPlanner(userId, roles);

        VoyageOutcome outcome = registerVoyage.register(toCommand(request));
        return switch (outcome) {
            case VoyageOutcome.Registered registered -> ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(VoyageResponse.from(registered.voyage()));
            case VoyageOutcome.AlreadyExists existing -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(VoyageDifferenceResponse.of(
                            VoyageResponse.from(existing.existing()), existing.difference()));
            case VoyageOutcome.NotFound notFound -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("指定された航海が見つかりません"));
        };
    }

    /** 差分を確認したうえで上書きする（US25）。 */
    @PutMapping("/{voyageNumber}")
    public ResponseEntity<?> update(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String voyageNumber,
            @Valid @RequestBody VoyageRequest request) {
        requireRoutingPlanner(userId, roles);

        if (!voyageNumber.equals(request.voyageNumber())) {
            // URL と本文が食い違ったまま処理すると、どちらの航海を直したのか分からなくなる
            throw new IllegalArgumentException("URL の航海番号と入力内容の航海番号が一致しません");
        }

        VoyageOutcome outcome = registerVoyage.overwrite(toCommand(request));
        return switch (outcome) {
            case VoyageOutcome.Registered registered ->
                    ResponseEntity.ok(VoyageResponse.from(registered.voyage()));
            case VoyageOutcome.NotFound notFound -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("指定された航海が見つかりません"));
            case VoyageOutcome.AlreadyExists existing ->
                    ResponseEntity.ok(VoyageResponse.from(existing.existing()));
        };
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(UserFacingMessage.of(e)));
    }

    /**
     * 航海スケジュールの登録・更新・検索は経路設計者の業務である。
     *
     * <p>営業担当者に開かない。開くと、営業が航海スケジュールと経路確定まで行えてしまい、
     * 職掌分離が崩れる（`Role` の定義に書いたとおり、経路設計者は独立したアクターである）。
     */
    private void requireRoutingPlanner(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ROUTING)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    private RegisterVoyageCommand toCommand(VoyageRequest request) {
        return new RegisterVoyageCommand(
                VoyageNumber.of(request.voyageNumber()),
                request.vesselName(),
                request.carrierName(),
                toCargoTypes(request.supportedCargoTypes()),
                Schedule.of(request.movements().stream()
                        .map(movement -> CarrierMovement.of(
                                location(movement.departureUnLocode(), "区間の出発地"),
                                location(movement.arrivalUnLocode(), "区間の到着地"),
                                movement.departureTime(), movement.arrivalTime()))
                        .toList()));
    }

    /**
     * 地点は登録済みのものに限る。
     *
     * <p>UN/LOCODE をそのまま受け入れると、打ち間違いが「実在しない港へ寄る航海」になる。
     * 経路候補算出（IT4）はその港を目的地にできず、原因は登録時ではなく利用時に現れる。
     */
    private Location location(String unLocode, String label) {
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "%sが見つかりません: %s".formatted(label, unLocode)));
    }

    private Set<CargoType> toCargoTypes(List<String> names) {
        Set<CargoType> cargoTypes = new LinkedHashSet<>();
        for (String name : names) {
            cargoTypes.add(java.util.Arrays.stream(CargoType.values())
                    .filter(cargoType -> cargoType.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "対応できる貨物種別は一覧から選んでください: " + name)));
        }
        return cargoTypes;
    }
}
