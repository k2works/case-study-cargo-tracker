package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.AssignRouteUseCase;
import com.example.bookingms.application.internal.BookCargoCommand;
import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.RequestConsultationUseCase;
import com.example.bookingms.application.internal.RequestRoutingUseCase;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.HazardClass;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/bookings")
public class CargoBookingController {

    private final BookCargoUseCase bookCargo;
    private final SearchCargoUseCase searchCargo;
    private final RequestRoutingUseCase requestRouting;
    private final AssignRouteUseCase assignRoute;
    private final RequestConsultationUseCase requestConsultation;
    private final CargoRepository cargoes;
    private final LocationRepository locations;
    private final Validator validator;

    public CargoBookingController(BookCargoUseCase bookCargo, SearchCargoUseCase searchCargo,
            RequestRoutingUseCase requestRouting, AssignRouteUseCase assignRoute,
            RequestConsultationUseCase requestConsultation, CargoRepository cargoes,
            LocationRepository locations, Validator validator) {
        this.bookCargo = bookCargo;
        this.searchCargo = searchCargo;
        this.requestRouting = requestRouting;
        this.assignRoute = assignRoute;
        this.requestConsultation = requestConsultation;
        this.cargoes = cargoes;
        this.locations = locations;
        this.validator = validator;
    }

    /**
     * 経路の割り当ては経路設計者の業務である。
     *
     * <p>営業が自分で経路を確定できると、職掌分離（[ADR-008]）が崩れる。
     */
    private void requireRoutingPlanner(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ROUTING)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    @GetMapping
    public BookingListResponse search(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(name = "type", required = false) CargoType type,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "routingStatus", required = false) RoutingStatus routingStatus) {
        AuthenticatedUser user = AuthenticatedUser.of(userId, roles);
        requireSalesOrRouting(user);

        SearchCargoUseCase.Result result =
                searchCargo.search(type, keyword, visibleRoutingStatus(user, routingStatus));
        return new BookingListResponse(
                result.cargoes().stream().map(BookingResponse::from).toList(),
                result.totalCount(), result.limit(), result.truncated());
    }

    /** 地点の選択肢。画面に UN/LOCODE を直接入力させないために返す。 */
    @GetMapping("/locations")
    public List<LocationResponse> locations(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireSales(userId, roles);
        return locations.findAll().stream().map(LocationResponse::from).toList();
    }

    /**
     * 危険物クラスの選択肢。
     *
     * <p>法定の分類であり、利用者が言葉を選べる項目ではない。自由入力にすると同じ意味の値が
     * 複数の字面で混ざり、経路設計・荷役が分類で判断できなくなる。
     */
    @GetMapping("/hazard-classes")
    public List<HazardClassResponse> hazardClasses(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireSales(userId, roles);
        return HazardClass.selectableList().stream().map(HazardClassResponse::from).toList();
    }

    /**
     * 予約の詳細（US06）。
     *
     * <p>営業担当者が引き渡す前に内容を確かめ、経路設計者が受け取った予約の中身を見るための入口。
     * URL に出るのは予約番号であり、内部の id ではない。
     */
    @GetMapping("/{bookingId}")
    public BookingResponse detail(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        // 経路設計者も見る。引き渡された予約の中身が見えないと、経路を組む判断ができない
        AuthenticatedUser user = AuthenticatedUser.of(userId, roles);
        requireSalesOrRouting(user);

        CargoSummary summary = cargoes.findByBookingId(bookingId)
                .filter(found -> visibleTo(user, found))
                .orElseThrow(CargoBookingController::notFound);
        return BookingResponse.from(summary);
    }

    /**
     * 経路設計を依頼する（US06）。
     *
     * <p>営業担当者の操作である。経路設計者が自分で依頼を立てられると、引き渡しの記録が
     * 「誰が渡したか」を表さなくなる。
     */
    @PostMapping("/{bookingId}/routing-request")
    public BookingResponse requestRouting(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireSales(userId, roles);

        return requestRouting.request(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    /**
     * 条件では経路が組めないことを営業へ差し戻す（US10・[ADR-020] 決定 7）。
     *
     * <p>経路設計者の操作である。「見つかりませんでした」で終わらせると、経路設計者の
     * 画面の中で行き止まりになり、荷主との条件交渉が始まらない。
     */
    @PostMapping("/{bookingId}/consultation-request")
    public BookingResponse requestConsultation(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireRoutingPlanner(userId, roles);

        return requestConsultation.request(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    /**
     * 選んだ経路を予約に割り当てる（US09 / US11・[ADR-019]）。
     *
     * <p>経路設計者の操作である。<strong>認可を入力の検査より先に置く</strong>（[ADR-016]）。
     * <strong>値の変換もメソッド本体で行う</strong>（引数を `Instant` で受け取ると、Spring は
     * 認可より先に変換を試み、失敗すると既定の 400 を返す。権限の無い相手に入力仕様を教える
     * ことになる。IT4 では実バックエンドでのみ再現した）。
     */
    @PutMapping("/{bookingId}/route")
    public BookingResponse assignRoute(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId,
            @RequestBody AssignRouteRequest request) {
        requireRoutingPlanner(userId, roles);

        CargoItinerary chosen = itineraryOf(request);
        return assignRoute.assign(bookingId, chosen, request.maxTransshipments())
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    /**
     * 入力を旅程へ変換する。
     *
     * <p>不正な入力は集約に届く前にここで {@link IllegalArgumentException} になる。集約の
     * 例外と同じ扱い（400）にするため、変換も入力の誤りとして扱う。
     */
    private CargoItinerary itineraryOf(AssignRouteRequest request) {
        if (request == null || request.legs() == null || request.legs().isEmpty()) {
            throw new IllegalArgumentException("割り当てる経路の区間を指定してください");
        }
        return CargoItinerary.of(request.legs().stream().map(this::legOf).toList());
    }

    private Leg legOf(AssignRouteRequest.LegRequest leg) {
        return Leg.of(VoyageNumber.of(leg.voyageNumber()),
                // 地点はマスタから引く。画面が送った名称を信じると、地点名の直しが 2 か所に分かれる
                requireLocation(leg.loadUnLocode(), "積込地"),
                requireLocation(leg.unloadUnLocode(), "荷降し地"),
                parseInstant(leg.loadTime(), "積込日時"),
                parseInstant(leg.unloadTime(), "荷降し日時"));
    }

    private Location requireLocation(String unLocode, String what) {
        if (unLocode == null || unLocode.isBlank()) {
            throw new IllegalArgumentException("%sを指定してください".formatted(what));
        }
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "%sが見つかりません: %s".formatted(what, unLocode)));
    }

    private Instant parseInstant(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%sを指定してください".formatted(what));
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException _) {
            // 入力値そのものは返さない（IT2 の決定）。何の項目が誤っているかだけを伝える
            throw new IllegalArgumentException(
                    "%sは ISO 8601（2026-09-01T09:00:00Z）の形式で指定してください".formatted(what));
        }
    }

    /**
     * 依頼できない状態への依頼は 409 で返す。
     *
     * <p>入力の誤り（400）ではない。入力は正しく、予約の状態がその操作を許さない。
     * 400 で返すと、画面は「入力を直してください」と伝えることになる。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(UserFacingMessage.of(e)));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> book(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody BookingRequest request) {
        requireSales(userId, roles);
        validate(request);

        Cargo booked = bookCargo.book(new BookCargoCommand(
                request.shipperId(), request.type(), request.weightKg(), request.quantity(),
                request.description(), request.lengthCm(), request.widthCm(), request.heightCm(),
                request.originUnLocode(), request.destinationUnLocode(),
                request.departureDate(), request.arrivalDeadline(),
                request.hazardousClass(), request.unNumber(), request.properShippingName(),
                request.minCelsius(), request.maxCelsius()));

        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booked));
    }

    /**
     * 入力の誤りは理由を添えて 400 で返す。
     *
     * <p>理由を返さないと、営業担当者は「登録したのに一覧に出ない」としか見えない。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(UserFacingMessage.of(e)));
    }

    /**
     * 貨物予約は営業担当者の業務である（ADR-008）。
     *
     * <p>{@code ROLE_SHIPPER} には開かない。利用者と荷主を結ぶキーが無く「自分の予約だけ」に
     * 絞り込めないため、開くと全荷主の予約が見える。US18（IT6）で紐付けと同時に広げ直す。
     *
     * <p>予約の中身は営業担当者と経路設計者が見る。経路を組む判断には内容が要る。
     */
    private void requireSalesOrRouting(AuthenticatedUser user) {
        if (!user.hasAnyRole(Role.ROLE_SALES, Role.ROLE_ROUTING)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /**
     * 経路設計者には、引き渡された予約の詳細だけを開く。
     *
     * <p>一覧を絞っても、予約番号を順に試せば詳細から同じ範囲が読める。**入口を 1 つ塞いでも、
     * 同じ範囲を返すもう 1 つの入口が開いていれば、絞りは無いのと同じ**。判定は集約の述語を
     * そのまま呼ぶ（一覧と別の判定を書かない）。
     */
    private boolean visibleTo(AuthenticatedUser user, CargoSummary summary) {
        return user.hasAnyRole(Role.ROLE_SALES) || summary.cargo().visibleToRoutingPlanner();
    }

    /**
     * 見えない予約と存在しない予約を、応答で区別しない（残作業 11）。
     *
     * <p>403 と 404 を打ち分けると、予約番号を順に試すだけで<strong>どの番号が実在するか</strong>
     * が分かる。内容は隠れても、営業がいま何件抱えているかは漏れる。番号は連番であり、
     * 総当たりは容易である。<strong>本文も同じにする</strong>。文言が違えば、そこから存在が読める。
     */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "指定された予約が見つかりません");
    }

    /**
     * 経路設計者に見せる一覧の範囲を、引き渡された予約に限る。
     *
     * <p>US06 のために一覧を開くが、全件を開くわけではない。経路設計者の仕事は「依頼された
     * 予約に経路を組む」ことであり、まだ引き渡されていない予約（営業が作業中のもの）を
     * 見る必要が無い。ADR-008 が「必要な範囲だけ開く」と決めた形をここでも守る。
     *
     * <p>営業担当者を兼ねる利用者は、営業として全件を見られる。
     */
    private RoutingStatus visibleRoutingStatus(AuthenticatedUser user, RoutingStatus requested) {
        if (user.hasAnyRole(Role.ROLE_SALES)) {
            return requested;
        }
        return RoutingStatus.ROUTING_REQUESTED;
    }

    private void requireSales(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SALES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /**
     * 入力の検査を認可のあとに行う。
     *
     * <p>{@code @Valid} は引数の解決時に走るため、権限の無い呼び出しでも本文が不正なら
     * 400 が返る。本人には「この操作はできない」ではなく「入力を直せ」と伝わり、
     * 権限が無いはずの相手にエンドポイントの入力仕様を教えることにもなる。
     */
    private void validate(BookingRequest request) {
        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
    }
}
