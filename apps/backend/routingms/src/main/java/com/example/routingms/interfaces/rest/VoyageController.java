package com.example.routingms.interfaces.rest;

import com.example.routingms.application.internal.RegisterVoyageCommand;
import com.example.routingms.application.internal.RegisterVoyageUseCase;
import com.example.routingms.application.internal.SearchVoyageUseCase;
import com.example.routingms.application.internal.VoyageOutcome;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageDifference;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.routingms.application.port.LocationRepository;
import com.example.routingms.application.port.VoyageSearchCriteria;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.shared.domain.model.Location;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** 航海番号が見つからないときの文言。指す先が同じなので 1 か所に置く。 */
    private static final String VOYAGE_NOT_FOUND = "指定された航海が見つかりません";

    private final RegisterVoyageUseCase registerVoyage;
    private final SearchVoyageUseCase searchVoyage;
    private final LocationRepository locations;
    private final Validator validator;

    public VoyageController(RegisterVoyageUseCase registerVoyage, SearchVoyageUseCase searchVoyage,
            LocationRepository locations, Validator validator) {
        this.registerVoyage = registerVoyage;
        this.searchVoyage = searchVoyage;
        this.locations = locations;
        this.validator = validator;
    }

    /**
     * 航海スケジュールを検索する（US07）。
     *
     * <p>出発地・目的地は UN/LOCODE で指定する。貨物種別を指定すると、その貨物を運べる航海
     * だけが残る（危険物・冷凍は運べる船が限られる）。
     */
    @GetMapping
    public VoyageListResponse search(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "departureFrom", required = false) Instant departureFrom,
            @RequestParam(name = "departureTo", required = false) Instant departureTo,
            @RequestParam(name = "cargoType", required = false) CargoType cargoType) {
        requireRoutingPlanner(userId, roles);

        SearchVoyageUseCase.Result result = searchVoyage.search(new VoyageSearchCriteria(
                blankToNull(origin), blankToNull(destination), departureFrom, departureTo,
                cargoType));

        return new VoyageListResponse(
                result.voyages().stream().map(VoyageResponse::from).toList(),
                result.totalCount(), result.limit(), result.truncated());
    }

    /**
     * 航海 1 件の内容を返す（US25）。
     *
     * <p>更新の画面が既存の内容を初期値にするために要る。番号だけを引き継いで空のフォームを
     * 出すと、10 区間ある航海の到着を 1 日ずらすのに全部打ち直すことになる。
     */
    @GetMapping("/{voyageNumber}")
    public ResponseEntity<Object> detail(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String voyageNumber) {
        requireRoutingPlanner(userId, roles);

        return searchVoyage.findByNumber(VoyageNumber.of(voyageNumber))
                .<ResponseEntity<Object>>map(voyage -> ResponseEntity.ok(VoyageResponse.from(voyage)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(VOYAGE_NOT_FOUND)));
    }

    /** 地点の選択肢。画面に UN/LOCODE を直接入力させないために返す。 */
    @GetMapping("/locations")
    public List<LocationResponse> locations(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireRoutingPlanner(userId, roles);
        return locations.findAll().stream()
                .map(location -> new LocationResponse(location.unLocode(), location.name()))
                .toList();
    }

    /** 空文字は「指定なし」として扱う。空文字で絞ると、条件に合う航海が 0 件になる。 */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 航海スケジュールを登録する（US24）。
     *
     * <p>同じ航海番号が既にあるときは 409 を返すが、これは失敗ではなく問いかけである。
     * 差分を添えて返し、画面は「上書きする」「やめる」を選ばせる。
     */
    @PostMapping
    public ResponseEntity<Object> register(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody VoyageRequest request) {
        requireRoutingPlanner(userId, roles);
        validate(request);

        VoyageOutcome outcome = registerVoyage.register(toCommand(request));
        return switch (outcome) {
            case VoyageOutcome.Registered(Voyage registered) -> ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(VoyageResponse.from(registered));
            case VoyageOutcome.AlreadyExists(Voyage existing, VoyageDifference difference) ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(VoyageDifferenceResponse.of(
                                    VoyageResponse.from(existing), difference));
            case VoyageOutcome.NotFound _ -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(VOYAGE_NOT_FOUND));
        };
    }

    /** 差分を確認したうえで上書きする（US25）。 */
    @PutMapping("/{voyageNumber}")
    public ResponseEntity<Object> update(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String voyageNumber,
            @RequestBody VoyageRequest request) {
        requireRoutingPlanner(userId, roles);
        validate(request);

        if (!voyageNumber.equals(request.voyageNumber())) {
            // URL と本文が食い違ったまま処理すると、どちらの航海を直したのか分からなくなる
            throw new IllegalArgumentException("URL の航海番号と入力内容の航海番号が一致しません");
        }

        VoyageOutcome outcome = registerVoyage.overwrite(toCommand(request));
        return switch (outcome) {
            case VoyageOutcome.Registered(Voyage updated) ->
                    ResponseEntity.ok(VoyageResponse.from(updated));
            case VoyageOutcome.NotFound _ -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(VOYAGE_NOT_FOUND));
            // 上書きの経路で重複が返ることはないが、返ったなら既存をそのまま示す
            case VoyageOutcome.AlreadyExists alreadyExists ->
                    ResponseEntity.ok(VoyageResponse.from(alreadyExists.existing()));
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

    /**
     * 入力の検査を認可のあとに行う。
     *
     * <p>{@code @Valid} は引数の解決時に走るため、権限の無い呼び出しでも本文が不正なら
     * 400 が返る。本人には「この操作はできない」ではなく「入力を直せ」と伝わり、
     * 権限が無いはずの相手にエンドポイントの入力仕様を教えることにもなる。
     * 同じ呼び出し元が本文次第で違う応答を受け取るのも紛らわしい。
     */
    private void validate(VoyageRequest request) {
        Set<ConstraintViolation<VoyageRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            // 文言はドメインと同じく、そのまま画面に出せるものにする
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
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
