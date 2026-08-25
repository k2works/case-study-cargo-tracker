package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.LocationMasterMissingException;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.application.port.RouteCandidateUnavailableException;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.VoyageNumber;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 経路設計者の手番（[ADR-008] の職掌分離）。
 *
 * <p>予約そのものの入口（{@link CargoBookingController}）から分けたのは、
 * <strong>手番が違うから</strong>である。経路の割り当て・相談の差し戻し・追跡番号の発行は
 * いずれも経路設計者だけが行い、営業の操作とは認可も失敗の意味も違う。
 *
 * <p>割る基準は「1 ファイル 500 行を超えたら責務で割る」（IT7 返済枠 0.11）。行数で切ると
 * 関係のあるものが分かれるため、<strong>手番という境目</strong>を使った。
 *
 * <p>両方の入口に共通する失敗の翻訳（400 / 409）は {@link BookingErrorHandlers} に置く。
 * 各コントローラに写すと、片方だけ直る形になる。
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class CargoRoutingController {

    private final BookingUseCases useCases;
    private final LocationRepository locations;

    public CargoRoutingController(BookingUseCases useCases, LocationRepository locations) {
        this.useCases = useCases;
        this.locations = locations;
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

        return useCases.requestConsultation().request(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(CargoRoutingController::notFound);
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
        return useCases.assignRoute().assign(bookingId, chosen, request.maxTransshipments())
                .map(result -> BookingResponse.from(result.cargo(), result.daysBeyondDeadline()))
                .orElseThrow(CargoRoutingController::notFound);
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
     * 経路を確認できなかったときは 503 で返す（[ADR-019]）。
     *
     * <p>入力の誤り（400）でも状態の不一致（409）でもない。409 にすると「航海スケジュールが
     * 変わった」と読め、経路設計者は何度探し直しても直らない作業に入る。
     */
    @ExceptionHandler(RouteCandidateUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRoutingUnavailable(
            RouteCandidateUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(UserFacingMessage.of(e)));
    }

    /**
     * <strong>こちら側の不備は 409 にしない</strong>（IT6 タスク 0.4）。
     *
     * <p>地点マスタの欠落は種データか複製の同期の問題であり、経路設計者が何度探し直しても
     * 直らない。409 と「経路をもう一度探してください」で返すと、直らない作業をさせたうえ、
     * 原因がどこにも残らない。
     *
     * <p><strong>利用者に作業を促さない文言</strong>で返す。中身（どの地点が無いか）は
     * 返さない——利用者には使い道が無く、こちらの構成を漏らすだけである。原因は例外として
     * 記録に残る。
     */
    @ExceptionHandler(LocationMasterMissingException.class)
    public ResponseEntity<ErrorResponse> handleOurOwnDefect(LocationMasterMissingException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("処理できませんでした。担当者にお問い合わせください"));
    }

    /**
     * 追跡番号を発行する（US14）。
     *
     * <p>経路設計者の操作である（`ui_design.md` の権限マトリクス）。<strong>確定した予約に
     * だけ発行できる</strong>——その判定は集約が持つ。
     *
     * <p>確定は `BookingStatus` だけを動かし `RoutingStatus` は `ROUTED` のままなので、
     * 確定した予約は経路設計者に見えたままである（[ADR-021] 決定 7）。
     */
    @PostMapping("/{bookingId}/tracking-number")
    public BookingResponse issueTrackingNumber(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireRoutingPlanner(userId, roles);

        return useCases.issueTrackingNumber().issue(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(CargoRoutingController::notFound);
    }

    /**
     * 見えない予約と存在しない予約を、応答で区別しない。
     *
     * <p>403 と 404 を打ち分けると、予約番号を順に試すだけで<strong>どの番号が実在するか</strong>
     * が分かる。番号は連番であり、総当たりは容易である。<strong>本文も同じにする</strong>
     * ——文言が違えば、そこから存在が読める（{@link CargoBookingController} と同じ文言）。
     */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "指定された予約が見つかりません");
    }
}
