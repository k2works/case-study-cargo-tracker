package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.BookCargoCommand;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.HazardClass;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.domain.model.Location;
import com.example.shared.auth.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/bookings")
public class CargoBookingController {

    private final BookingUseCases useCases;
    private final CargoRepository cargoes;
    private final LocationRepository locations;
    private final Validator validator;

    public CargoBookingController(BookingUseCases useCases, CargoRepository cargoes,
            LocationRepository locations, Validator validator) {
        this.useCases = useCases;
        this.cargoes = cargoes;
        this.locations = locations;
        this.validator = validator;
    }

    @GetMapping
    public BookingListResponse search(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(name = "type", required = false) CargoType type,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "routingStatus", required = false) RoutingStatus routingStatus,
            // 経路設計者が「追跡番号の発行を待っている予約」を取り出すために要る（US13-3）。
            // 件数だけ出しても、そこから対象へ行けなければ仕事は進まない
            @RequestParam(name = "bookingStatus", required = false) BookingStatus bookingStatus) {
        AuthenticatedUser user = AuthenticatedUser.of(userId, roles);
        requireSalesOrRouting(user);

        SearchCargoUseCase.Result result = useCases.searchCargo()
                .search(type, keyword, visibleRoutingStatuses(user, routingStatus), bookingStatus);
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
        // 経路設計者も見る。引き渡された予約の中身が見えないと、経路を組む判断ができない。
        // **追跡管理者と荷役作業員も読む**（IT10 レビュー）——誤配に最初に気づくのも、
        // キャンセルを承認するのも追跡管理者であり、どちらの一覧からもここへ渡す導線が
        // ある。**読むだけである**（操作の可否は集約の述語が決め、画面が出し分ける）
        AuthenticatedUser user = AuthenticatedUser.of(userId, roles);
        requireBookingReader(user);

        CargoSummary summary = cargoes.findByBookingId(bookingId)
                .filter(found -> visibleTo(user, found))
                .orElseThrow(CargoBookingController::notFound);
        return BookingResponse.from(summary, daysBeyondDeadlineOf(summary.cargo()),
                this::locationNameOf);
    }

    /**
     * 港の名前を地点マスタから引く（IT10 レビュー低 15）。
     *
     * <p><strong>旅程からは引けない。</strong>誤配した港は定義上、予定ルートの外にある。
     * 旅程の中を探しても見つからないため、地点マスタを引く。
     *
     * <p>引けなくても記録そのものは返す（呼び出し先で {@code null} になる）。
     */
    private java.util.Optional<String> locationNameOf(String unLocode) {
        return locations.findByUnLocode(unLocode).map(Location::name);
    }

    /**
     * 到着予定が希望期限を超える日数（US28-6）。超えないか、判断できないなら {@code null}。
     *
     * <p><strong>伝えるのは営業である</strong>（通知は代替。[ADR-026] 決定 5）。割り当てた
     * 直後の画面にしか出さないと、経路設計者がメモを取り損ねた時点で誰も伝えられなくなる。
     *
     * <p><strong>暦が引けなくても詳細は開く。</strong>割り当てのときはこちら側の不備として
     * 断るが（{@code AssignRouteUseCase}）、読むだけの詳細まで落とすと、マスタの不備で
     * 予約が 1 件も開けなくなる。超過の表示はそこまでの価値を持たない。
     */
    private Long daysBeyondDeadlineOf(Cargo cargo) {
        return locations.timeZoneOf(cargo.routeSpecification().destination().unLocode())
                .flatMap(cargo::daysBeyondDeadline)
                .orElse(null);
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

        return useCases.requestRouting().request(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    /**
     * 予約の日程を訂正する（US06 の訂正・IT6 タスク 0.11）。
     *
     * <p>営業担当者の操作である。条件協議の結果が「期限を延ばす」だったとき、予約を直せないと
     * 再依頼しても同じ結果になる。<strong>直せるのは日程だけ</strong>——出発地・目的地・貨物の
     * 仕様を変えるならそれは別の予約である。
     *
     * <p><strong>入力の検査は `@Valid` を使わずメソッド本体で行う</strong>（[ADR-016] 決定 2）。
     */
    @PutMapping("/{bookingId}/schedule")
    public BookingResponse reviseSchedule(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId,
            @RequestBody ReviseScheduleRequest request) {
        requireSales(userId, roles);

        return useCases.reviseSchedule()
                .revise(bookingId, parseDate(request.departureDate(), "出発希望日"),
                        parseDate(request.arrivalDeadline(), "到着期限"))
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    /**
     * 日付を読む。
     *
     * <p>形式の誤りは 400 で返す。読めない値をそのまま渡すと、集約が「必須です」と断り、
     * 利用者には「入力していないのに必須と言われる」と見える。
     */
    private static java.time.LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException _) {
            throw new IllegalArgumentException(label + "の形式が正しくありません: " + value);
        }
    }

    /**
     * 経路を荷主へ通知する（US12-3・US12-4・[ADR-021] 決定 1・決定 2）。
     *
     * <p>営業担当者の操作である。荷主とのやりとりを持っているのは営業であり、経路設計者が
     * 荷主へ直接連絡すると、営業が把握していない約束ができる。
     *
     * <p><strong>メールは送らない。</strong>通知の仕組みは US19 で入る。ここで残すのは
     * 「通知したという業務上の事実」であり、それを画面が見せる。
     *
     * <p>できない状態への通知は 409 で返す（判定は集約が持つ）。
     */
    @PostMapping("/{bookingId}/route-notification")
    public BookingResponse notifyShipper(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireSales(userId, roles);

        // 記録に残すのは「誰が」であり、システムではない
        return useCases.notifyShipper().notifyShipper(bookingId, userId)
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    /**
     * 荷主の合意を得て予約を確定する（US13-2）。
     *
     * <p>営業担当者の操作である。<strong>通知していない予約は確定できない</strong>
     * （[ADR-021] 決定 1）。その判定は集約が持ち、ここには書き写さない——書き写すと、
     * 入口が増えた数だけ判定が増え、どれかが古くなる。
     */
    @PutMapping("/{bookingId}/confirm")
    public BookingResponse confirm(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireSales(userId, roles);

        return useCases.confirmBooking().confirm(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    /**
     * 荷主が変更を希望したので経路設計へ戻す（US13-4・[ADR-021] 決定 4）。
     *
     * <p>営業担当者の操作である。戻すと経路の状態も作業待ちに戻り、経路設計者の一覧に現れる。
     */
    @PutMapping("/{bookingId}/return-to-routing")
    public BookingResponse returnToRouting(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireSales(userId, roles);

        return useCases.returnToRouting().returnToRouting(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(CargoBookingController::notFound);
    }

    @PostMapping
    public ResponseEntity<BookingResponse> book(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody BookingRequest request) {
        requireSales(userId, roles);
        validate(request);

        Cargo booked = useCases.bookCargo().book(new BookCargoCommand(
                request.shipperId(), request.type(), request.weightKg(), request.quantity(),
                request.description(), request.lengthCm(), request.widthCm(), request.heightCm(),
                request.originUnLocode(), request.destinationUnLocode(),
                request.departureDate(), request.arrivalDeadline(),
                request.hazardousClass(), request.unNumber(), request.properShippingName(),
                request.minCelsius(), request.maxCelsius()));

        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booked));
    }

    /**
     * 予約の詳細を読める人。
     *
     * <p><strong>一覧は広げない。</strong>一覧まで開くと、追跡管理者が営業の抱えている
     * 案件を横断して眺められる——例外や承認から辿る 1 件を読むこととは別の話である。
     */
    private void requireBookingReader(AuthenticatedUser user) {
        if (!user.hasAnyRole(Role.ROLE_SALES, Role.ROLE_ROUTING, Role.ROLE_TRACKER,
                Role.ROLE_HANDLER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
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
        // 追跡管理者・荷役は輸送中の貨物を扱う。経路設計の依頼有無で絞ると、
        // 誤配や承認の一覧から辿った予約が「見つかりません」になる
        return user.hasAnyRole(Role.ROLE_SALES, Role.ROLE_TRACKER, Role.ROLE_HANDLER)
                || summary.cargo().visibleToRoutingPlanner();
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
     * 経路設計者に見せる一覧の範囲を、集約の判定から導く。
     *
     * <p>US06 のために一覧を開くが、全件を開くわけではない。まだ引き渡されていない予約
     * （営業が作業中のもの）は対象ではない。[ADR-008] が「必要な範囲だけ開く」と決めた形を
     * ここでも守る。
     *
     * <p><strong>範囲は {@link RoutingStatus#openToRoutingPlanner()} から導く。</strong>
     * ここで別の判定を書くと、詳細（{@code visibleTo}）が開く範囲を広げても一覧が古い範囲の
     * ままになる。IT5 のレビューで、詳細だけが {@code ROUTED} を開き一覧が落としていた。
     *
     * <p>経路設計者が絞り込みを指定したときは、開いてよい範囲との積を取る。指定で範囲を
     * 広げることはできない。
     *
     * <p>営業担当者を兼ねる利用者は、営業として全件を見られる。
     */
    private Collection<RoutingStatus> visibleRoutingStatuses(
            AuthenticatedUser user, RoutingStatus requested) {
        if (user.hasAnyRole(Role.ROLE_SALES)) {
            return requested == null ? List.of() : List.of(requested);
        }
        List<RoutingStatus> open = RoutingStatus.openToRoutingPlanner();
        if (requested == null) {
            return open;
        }
        return open.contains(requested) ? List.of(requested) : open;
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
