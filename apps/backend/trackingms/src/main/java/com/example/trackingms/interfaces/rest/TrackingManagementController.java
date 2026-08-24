package com.example.trackingms.interfaces.rest;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.trackingms.application.internal.ManageTrackingUseCase;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingStatus;
import com.example.trackingms.interfaces.rest.TrackingManagementRequests.ManualUpdateRequest;
import com.example.trackingms.interfaces.rest.TrackingManagementRequests.RaiseExceptionRequest;
import com.example.trackingms.interfaces.rest.TrackingManagementRequests.ResolveExceptionRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 貨物状態の管理（US17・US19・US20）。
 *
 * <p><strong>認可は入力検証より先に置く</strong>（[ADR-016]）。@Valid が先に走ると、
 * 権限の無い相手に入力仕様を教えることになる。
 *
 * <p>状態を動かせるのは<strong>追跡管理者だけ</strong>である。例外の起票は
 * <strong>荷役作業員にも開く</strong>——破損・紛失に最初に気づくのは港にいる人である
 * （US20 のアクターは 2 つ）。
 */
@RestController
@RequestMapping("/api/v1/tracking/manage")
public class TrackingManagementController {

    private final ManageTrackingUseCase manage;

    /** 表示の暦。日時は業務のタイムゾーンで出す（[ADR-010]）。 */
    private final java.time.ZoneId zone;

    public TrackingManagementController(ManageTrackingUseCase manage, java.time.Clock clock) {
        this.manage = manage;
        this.zone = clock.getZone();
    }

    /** 起票できる例外の種別（[ADR-024] 決定 11）。**画面が一覧を持たない**。 */
    @GetMapping("/exception-types")
    public List<ExceptionTypeResponse> exceptionTypes(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireTrackerOrHandler(userId, roles);

        return ExceptionType.raisableTypes().stream().map(ExceptionTypeResponse::from).toList();
    }

    /** 未解決の例外の件数（横断規約）。**そこから一覧へ辿れる**。 */
    @GetMapping("/exceptions/open")
    public OpenExceptionSummary openExceptions(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireExceptionReader(userId, roles);

        List<TrackingActivity> open = manage.withOpenExceptions();
        return new OpenExceptionSummary(open.size(),
                (int) open.stream().filter(TrackingActivity::hasUrgentException).count());
    }

    /** 未解決の例外がある貨物の一覧。**件数の遷移先である**。 */
    @GetMapping("/exceptions")
    public List<ManagedTrackingResponse> openExceptionList(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireExceptionReader(userId, roles);

        return manage.withOpenExceptions().stream()
                .map(activity -> ManagedTrackingResponse.from(activity, List.of(), List.of(), zone))
                .toList();
    }

    /**
     * その貨物から手で進められる状態（US17-2）。
     *
     * <p><strong>進める先だけを返す。</strong>戻る向きの選択肢を出しておいて 409 で断るのは、
     * 押せるのに断られる操作を出すことである。判定は集約の {@code canAdvanceTo} 1 つに置く。
     */
    @GetMapping("/{trackingNumber}/statuses")
    public List<TrackingStatusResponse> advanceableStatuses(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String trackingNumber) {
        requireTracker(userId, roles);

        TrackingActivity activity = manage.find(trackingNumber)
                .orElseThrow(TrackingManagementController::notFound);
        return java.util.Arrays.stream(TrackingStatus.values())
                .filter(activity.trackingStatus()::canAdvanceTo)
                .map(TrackingStatusResponse::from)
                .toList();
    }

    /** 1 件を開く（US17-1）。 */
    @GetMapping("/{trackingNumber}")
    public ManagedTrackingResponse find(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String trackingNumber) {
        requireTrackerOrHandler(userId, roles);

        TrackingActivity activity = manage.find(trackingNumber)
                .orElseThrow(TrackingManagementController::notFound);
        return ManagedTrackingResponse.from(activity, manage.events(activity), manage.exceptions(activity), zone);
    }

    /**
     * 状態を手で更新する（US17-2）。
     *
     * <p><strong>追跡管理者だけ。</strong>荷役作業員は記録した作業から追跡が動く経路を
     * すでに持っており、そのうえで手でも動かせると、同じ貨物が 2 つの経路から動く。
     */
    @PostMapping
    public ManagedTrackingResponse updateStatus(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody ManualUpdateRequest request) {
        requireTracker(userId, roles);

        TrackingActivity updated = manage.updateStatus(request.trackingNumber(), request.status(),
                        request.locationUnLocode(), parseInstant(request.occurredAt()))
                .orElseThrow(TrackingManagementController::notFound);
        return ManagedTrackingResponse.from(updated, manage.events(updated), manage.exceptions(updated), zone);
    }

    /**
     * 例外を起票する（US19-1・US20-1）。
     *
     * <p><strong>荷役作業員にも開く。</strong>破損・紛失に最初に気づくのは港にいる人で
     * ある。追跡管理者だけに絞ると、気づいた人が伝える手段を持たない。
     */
    @PostMapping("/exceptions")
    public ManagedTrackingResponse raiseException(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody RaiseExceptionRequest request) {
        requireTrackerOrHandler(userId, roles);

        TrackingActivity raised = manage.raiseException(request.trackingNumber(),
                        request.exceptionType(), request.description())
                .orElseThrow(TrackingManagementController::notFound);
        return ManagedTrackingResponse.from(raised, manage.events(raised), manage.exceptions(raised), zone);
    }

    /**
     * 例外を解決する（US19-4）。
     *
     * <p><strong>追跡管理者だけ。</strong>解決したかどうかは荷主への説明責任を伴う判断で
     * あり、気づいた人が閉じられると「見つからないまま解決」になりうる。
     */
    @PostMapping("/exceptions/{exceptionId}/resolve")
    public ManagedTrackingResponse resolveException(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable Long exceptionId,
            @RequestBody ResolveExceptionRequest request) {
        requireTracker(userId, roles);

        // パスの番号を正とする。本文の番号と食い違うなら、画面が組み立てを誤っている
        TrackingActivity resolved = manage.resolveException(request.trackingNumber(), exceptionId,
                        request.resolutionNotes(), parseDate(request.newEstimatedArrival()))
                .orElseThrow(TrackingManagementController::notFound);
        return ManagedTrackingResponse.from(resolved, manage.events(resolved), manage.exceptions(resolved), zone);
    }

    /** 起票できる種別（[ADR-024] 決定 11）。 */
    public record ExceptionTypeResponse(String exceptionType, String label, boolean urgent) {
        static ExceptionTypeResponse from(ExceptionType type) {
            return new ExceptionTypeResponse(type.name(), type.label(), type.urgent());
        }
    }

    /** 手で進められる状態。 */
    public record TrackingStatusResponse(String status, String label) {
        static TrackingStatusResponse from(TrackingStatus status) {
            return new TrackingStatusResponse(status.name(), status.label());
        }
    }

    /** 未解決の例外の件数（横断規約）。 */
    public record OpenExceptionSummary(int count, int urgentCount) {
    }

    /** 状態を動かすのは追跡管理者の業務（[ADR-008]）。 */
    private void requireTracker(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_TRACKER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /**
     * 未解決の例外を<strong>読むだけ</strong>は営業担当者にも開く（IT9 返済枠 0.9・
     * IT8 レビュー #19）。
     *
     * <p>荷主は公開の追跡照会で「ご依頼元の営業担当へ」と案内される。ところが営業には
     * 例外に気づく手段が無く、<strong>電話を受けてから追跡管理者を探す</strong>ことに
     * なっていた。案内した先に何も無いのでは、案内が行き止まりである。
     *
     * <p><strong>起票と解決には開かない。</strong>解決したかどうかは荷主への説明責任を
     * 伴う判断であり、追跡管理者の業務である（[ADR-008]）。
     */
    private void requireExceptionReader(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles)
                .hasAnyRole(Role.ROLE_TRACKER, Role.ROLE_HANDLER, Role.ROLE_SALES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /** 参照と起票は荷役作業員にも開く。破損・紛失に最初に気づくのは港にいる人である。 */
    private void requireTrackerOrHandler(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_TRACKER, Role.ROLE_HANDLER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "追跡番号が見つかりません");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("日時を指定してください");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException _) {
            // 入力値そのものは返さない（IT2 の決定）。何の項目が誤っているかだけを伝える
            throw new IllegalArgumentException(
                    "日時は ISO 8601（2026-08-23T09:00:00Z）の形式で指定してください");
        }
    }

    /** 新しい到着予定日は<strong>任意</strong>である。空なら据え置く（US19-4）。 */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException _) {
            throw new IllegalArgumentException(
                    "到着予定日は YYYY-MM-DD の形式で指定してください");
        }
    }

    /** 入力の誤りは理由を添えて 400 で返す。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 業務の状態として行えない操作は 409 で返す。
     *
     * <p><strong>400 にしない。</strong>入力そのものは正しく、直すところが無い。
     * 400 だと利用者は打ち直しを試み、そのたびに同じ答えが返る。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    /** 利用者に見せる文言。 */
    public record ErrorResponse(String message) {
    }
}
