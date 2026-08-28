package com.example.handlingms.interfaces.rest;

import com.example.handlingms.application.internal.commandservices.ManageCustomsDeclarationUseCase;
import com.example.handlingms.application.internal.commandservices.RegisterCustomsDeclarationCommand;
import com.example.handlingms.application.internal.commandservices.RegisterCustomsDeclarationUseCase;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.interfaces.rest.CustomsRequests.RegisterCustomsDeclarationRequest;
import com.example.handlingms.interfaces.rest.CustomsRequests.UpdateCustomsStatusRequest;
import com.example.handlingms.interfaces.rest.CustomsResponses.CustomsDeclarationDetailResponse;
import com.example.handlingms.interfaces.rest.CustomsResponses.CustomsDeclarationResponse;
import com.example.handlingms.interfaces.rest.CustomsResponses.CustomsSearchResponse;
import com.example.handlingms.interfaces.rest.CustomsResponses.CustomsStatusResponse;
import com.example.handlingms.interfaces.rest.CustomsResponses.OverdueCustomsSummary;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
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

/**
 * 通関申告（US29・UC21）。
 *
 * <p><strong>登録は荷役作業員、状態の更新は追跡管理者。閲覧は両方</strong>
 * （[ADR-025] 決定 6）。追跡管理者は状態を更新する側であり、申告そのものは出さない。
 *
 * <p><strong>認可は入力検証より先に置く</strong>（[ADR-016]）。検証が先に走ると、
 * 権限の無い相手に「何が必須か」を教えることになる。
 */
@RestController
@RequestMapping("/api/v1/customs")
public class CustomsController {

    private final RegisterCustomsDeclarationUseCase register;
    private final ManageCustomsDeclarationUseCase manage;

    public CustomsController(RegisterCustomsDeclarationUseCase register,
            ManageCustomsDeclarationUseCase manage) {
        this.register = register;
        this.manage = manage;
    }

    /** 通関状態の選択肢。**画面が一覧を持たない**。 */
    @GetMapping("/statuses")
    public List<CustomsStatusResponse> statuses(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireHandlerOrTracker(userId, roles);

        return Arrays.stream(CustomsStatus.values()).map(CustomsStatusResponse::from).toList();
    }

    /** 留置 3 日超の件数（US29-6）。**そこから対象一覧へ辿れる**（横断規約）。 */
    @GetMapping("/overdue")
    public OverdueCustomsSummary overdue(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireHandlerOrTracker(userId, roles);

        return new OverdueCustomsSummary(manage.countHeldOverdue());
    }

    /**
     * 一覧・検索（US29-7）。
     *
     * <p>{@code unsettledOnly} は<strong>未決着（審査中・留置）だけ</strong>に絞る。
     * <strong>追跡管理者の朝の仕事は「未決着を上から片付ける」ことである。</strong>
     *
     * <p><strong>総件数と切り捨てを返す。</strong>黙って切ると「一覧に出ていないから無い」と
     * 読まれる（予約一覧と同じ形）。
     */
    @GetMapping
    public CustomsSearchResponse search(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(required = false) String bookingId,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean unsettledOnly) {
        requireHandlerOrTracker(userId, roles);

        ManageCustomsDeclarationUseCase.CustomsSearchResult result =
                manage.search(bookingId, trackingNumber, status, unsettledOnly);
        return new CustomsSearchResponse(
                result.declarations().stream().map(this::toResponse).toList(),
                result.totalCount(), result.limit(), result.truncated());
    }

    /** 詳細（US29-8）。状態変更の履歴を伴う。 */
    @GetMapping("/{declarationId}")
    public CustomsDeclarationDetailResponse find(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable long declarationId) {
        requireHandlerOrTracker(userId, roles);

        return toDetail(manage.find(declarationId).orElseThrow(CustomsController::notFound));
    }

    /** 申告の登録（US29-1）。**荷役作業員だけ**。 */
    @PostMapping
    public ResponseEntity<CustomsDeclarationDetailResponse> register(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody RegisterCustomsDeclarationRequest request) {
        requireHandler(userId, roles);

        CustomsDeclaration declared = register.register(new RegisterCustomsDeclarationCommand(
                request.trackingNumber(), request.declarationNumber(),
                parseInstant(request.declaredAt()), request.remarks(), userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(declared));
    }

    /** 状態の更新（US29-2）。**追跡管理者だけ**。理由は集約が必須にする。 */
    @PutMapping("/{declarationId}/status")
    public CustomsDeclarationDetailResponse updateStatus(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable long declarationId,
            @RequestBody UpdateCustomsStatusRequest request) {
        requireTracker(userId, roles);

        return toDetail(manage
                .updateStatus(declarationId, request.status(), userId, request.reason())
                .orElseThrow(CustomsController::notFound));
    }

    private CustomsDeclarationResponse toResponse(CustomsDeclaration declaration) {
        return CustomsDeclarationResponse.from(declaration, manage.today(), manage.zone(),
                ManageCustomsDeclarationUseCase.HELD_OVERDUE_DAYS);
    }

    private CustomsDeclarationDetailResponse toDetail(CustomsDeclaration declaration) {
        return CustomsDeclarationDetailResponse.from(declaration, manage.today(), manage.zone(),
                ManageCustomsDeclarationUseCase.HELD_OVERDUE_DAYS);
    }

    /**
     * 申告を出すのは荷役作業員の業務である（[ADR-025] 決定 6）。
     *
     * <p>追跡管理者には開かない。追跡管理者は状態を更新する側であり、申告を出す側では
     * ない——両方できると、出した本人が自分で通関済にできる。
     */
    private void requireHandler(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_HANDLER)) {
            throw forbidden();
        }
    }

    /**
     * 状態を動かすのは追跡管理者の業務である。
     *
     * <p>荷役作業員には開かない。出した本人が通関済にできると、税関の判断を待たずに
     * 引き取れてしまう。
     */
    private void requireTracker(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_TRACKER)) {
            throw forbidden();
        }
    }

    /** 閲覧は両方に開く。荷役作業員は自分が出した申告の行方を追う。 */
    private void requireHandlerOrTracker(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles)
                .hasAnyRole(Role.ROLE_HANDLER, Role.ROLE_TRACKER)) {
            throw forbidden();
        }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "通関申告が見つかりません");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("申告日時を指定してください");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException _) {
            // 入力値そのものは返さない（IT2 の決定）。何の項目が誤っているかだけを伝える
            throw new IllegalArgumentException(
                    "申告日時は ISO 8601（2026-08-23T09:00:00Z）の形式で指定してください");
        }
    }

    /** 入力の誤り。**理由を返さないと、利用者は何を直せばよいか分からない**。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 業務の規則に触れた（[ADR-025] 決定 7）。
     *
     * <p><strong>400 では返さない。</strong>入力が誤っているのではなく、いまはその操作を
     * 行えない、という意味である。400 だと利用者は入力を直そうとして、直しようがない。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
}
