package com.example.handlingms.interfaces.rest;

import com.example.handlingms.application.internal.RegisterHandlingActivityCommand;
import com.example.handlingms.application.internal.RegisterHandlingActivityUseCase;
import com.example.handlingms.application.port.CargoLookupUnavailableException;
import com.example.handlingms.application.port.CargoSnapshotFinder;
import com.example.handlingms.application.port.HandlingActivityRepository;
import com.example.handlingms.application.port.LocationRepository;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import com.example.handlingms.domain.model.HandlingType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 荷役作業の記録（US15・US16）。
 *
 * <p><strong>認可を入力の検査より先に置く</strong>（[ADR-016]）。順序を逆にすると、権限の
 * 無い呼び出しでも本文が不正なら 400 が返り、権限が無いはずの相手に入力仕様を教える。
 */
@RestController
@RequestMapping("/api/v1/handling")
public class HandlingActivityController {

    /** 一覧の上限。上限が無いと、件数が増えた日に一覧が開かなくなる。 */
    private static final int HISTORY_LIMIT = 100;

    private final RegisterHandlingActivityUseCase registerActivity;
    private final HandlingActivityRepository activities;
    private final LocationRepository locations;
    private final CargoSnapshotFinder cargoes;

    public HandlingActivityController(RegisterHandlingActivityUseCase registerActivity,
            HandlingActivityRepository activities, LocationRepository locations,
            CargoSnapshotFinder cargoes) {
        this.registerActivity = registerActivity;
        this.activities = activities;
        this.locations = locations;
        this.cargoes = cargoes;
    }

    /**
     * 荷役作業を記録する。
     *
     * <p><strong>値の変換もメソッド本体で行う</strong>（[ADR-016]）。引数を {@code Instant} で
     * 受け取ると、Spring は認可より先に変換を試みる。
     */
    @PostMapping
    public ResponseEntity<HandlingActivityResponse> register(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody HandlingActivityRequest request) {
        requireHandler(userId, roles);

        HandlingActivityResponse response = registerActivity.register(commandOf(request, userId))
                .map(HandlingActivityResponse::from)
                .orElseThrow(HandlingActivityController::cargoNotFound);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 1 つの貨物に何が起きたかを、時系列で返す（US15 の履歴）。
     *
     * <p>追跡管理者にも開く。<strong>参照のみ</strong>で、記録はできない。
     *
     * <p><strong>追跡番号でも引ける。</strong>荷役作業員も追跡管理者も、手元にあるのは
     * 追跡番号である。予約番号でしか引けないと、「あの貨物はもう積んだか」という
     * 問い合わせに誰も答えられない。予約番号は、記録したあとに応答から分かる。
     */
    @GetMapping
    public List<HandlingActivityResponse> history(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(required = false) String bookingId,
            @RequestParam(required = false) String trackingNumber) {
        requireHandlerOrTracker(userId, roles);

        CargoBookingId target = resolve(bookingId, trackingNumber);
        return activities.findByBookingId(target, HISTORY_LIMIT).stream()
                .map(HandlingActivityResponse::from)
                .toList();
    }

    /**
     * どの貨物の履歴かを決める。
     *
     * <p>追跡番号で指定されたときは ACL で貨物を引く。<strong>見つからないことと
     * 確かめられないことを分ける</strong>のは記録のときと同じである。
     */
    private CargoBookingId resolve(String bookingId, String trackingNumber) {
        if (bookingId != null && !bookingId.isBlank()) {
            return CargoBookingId.of(bookingId);
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("追跡番号か予約番号を指定してください");
        }
        return cargoes.findByTrackingNumber(HandlingTrackingNumber.of(trackingNumber))
                .map(cargo -> CargoBookingId.of(cargo.bookingId()))
                .orElseThrow(HandlingActivityController::cargoNotFound);
    }

    /** 作業場所の選択肢（US15-3）。自由入力にすると、綴りの揺れた港が記録に入る。 */
    @GetMapping("/locations")
    public List<LocationResponse> locations(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireHandlerOrTracker(userId, roles);

        return locations.findAll().stream().map(LocationResponse::from).toList();
    }

    /** 荷役の種別と、その要件（[ADR-023] 決定 1）。画面に対訳表と分岐を置かないために返す。 */
    @GetMapping("/types")
    public List<HandlingTypeResponse> types(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireHandlerOrTracker(userId, roles);

        return java.util.Arrays.stream(HandlingType.values())
                .map(HandlingTypeResponse::from)
                .toList();
    }

    private RegisterHandlingActivityCommand commandOf(HandlingActivityRequest request,
            String userId) {
        if (request == null) {
            throw new IllegalArgumentException("荷役作業の内容を指定してください");
        }
        HandlingType.parse(request.type());
        return new RegisterHandlingActivityCommand(
                request.trackingNumber(),
                request.type(),
                request.locationUnLocode(),
                parseInstant(request.completionTime()),
                // 作業者は名乗りから取る。本文で受け取ると、他人の名前で記録できる
                userId,
                request.voyageNumber(),
                request.consigneeConfirmation());
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("作業日時を指定してください");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException _) {
            // 入力値そのものは返さない（IT2 の決定）。何の項目が誤っているかだけを伝える
            throw new IllegalArgumentException(
                    "作業日時は ISO 8601（2026-08-23T09:00:00Z）の形式で指定してください");
        }
    }

    /**
     * 荷役の記録は荷役作業員の業務である（[ADR-008]）。
     *
     * <p>追跡管理者には開かない。追跡は<strong>結果を見る</strong>役割であり、
     * 記録できると「見ている人が動かす」ことになる。
     */
    private void requireHandler(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_HANDLER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /** 参照は追跡管理者にも開く。何が起きたかを追う役割である。 */
    private void requireHandlerOrTracker(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles)
                .hasAnyRole(Role.ROLE_HANDLER, Role.ROLE_TRACKER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /**
     * 追跡番号の貨物が見つからない（US15-6）。
     *
     * <p>作業員が読み違えた番号を打った、というのが最も多い。<strong>何を直せばよいかを
     * 伝える。</strong>
     */
    private static ResponseStatusException cargoNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "指定された追跡番号の貨物が見つかりません。番号を確かめてください");
    }

    /**
     * 貨物を確かめられなかったときは 503 で返す。
     *
     * <p><strong>404 にしない。</strong>「その番号は存在しません」と伝えると、作業員は番号を
     * 疑って打ち直し続ける。直らない作業をさせたうえ、原因はどこにも残らない。
     */
    @ExceptionHandler(CargoLookupUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleLookupUnavailable(
            CargoLookupUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 入力の誤りは理由を添えて 400 で返す。
     *
     * <p>理由を返さないと、荷役作業員は「押したのに何も起きない」としか見えない。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * すでに記録されている作業は 409 で返す（IT8 返済枠 0.8）。
     *
     * <p><strong>400 にしない。</strong>入力そのものは正しく、直すところが無い。
     * 400 だと作業員は打ち直しを試み、そのたびに同じ答えが返る。「もう入っている」
     * ことが伝われば、次にすることは履歴を見ることである。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyRecorded(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
}
