package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.CancellationOutcome;
import com.example.bookingms.application.internal.DecideCancellationUseCase;
import com.example.bookingms.application.internal.RequestCancellationUseCase;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.domain.model.CancellationRequest;
import com.example.bookingms.interfaces.rest.CancellationResponses.CancellationOutcomeResponse;
import com.example.bookingms.interfaces.rest.CancellationResponses.CancellationResponse;
import com.example.bookingms.interfaces.rest.CancellationResponses.PendingCancellationResponse;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * キャンセルの申請と承認（US30・UC22）。
 *
 * <p><strong>申請は営業担当者、承認は追跡管理者。</strong>自分の申請を自分で承認できると
 * 承認の意味が無くなる。
 *
 * <p><strong>認可は入力検証より先に置く</strong>（[ADR-016]）。
 *
 * <p><strong>承認待ちの一覧は {@code /api/v1/bookings/} の下に置かない。</strong>
 * {@code /api/v1/bookings/{bookingId}} が {@code cancellations} を予約 ID として拾う
 * ——モックでも Spring のパス変数でも同じ衝突が起きる（IT9 でモックが実際にそうなった）。
 */
@RestController
public class CancellationController {

    private final RequestCancellationUseCase request;
    private final DecideCancellationUseCase decide;
    private final CargoRepository cargoes;

    public CancellationController(RequestCancellationUseCase request,
            DecideCancellationUseCase decide, CargoRepository cargoes) {
        this.request = request;
        this.decide = decide;
        this.cargoes = cargoes;
    }

    /** 承認待ちの一覧（US30-4）。**件数の遷移先である**。追跡管理者のみ。 */
    @GetMapping("/api/v1/cancellations")
    public List<PendingCancellationResponse> awaiting(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireTracker(userId, roles);

        return decide.awaitingDecision().stream()
                .map(this::toPending)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /** その予約のキャンセル申請。無ければ本文なしで返す。 */
    @GetMapping("/api/v1/bookings/{bookingId}/cancellation")
    public ResponseEntity<CancellationResponse> find(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireSalesOrTracker(userId, roles);

        return decide.latestFor(bookingId)
                .map(found -> ResponseEntity.ok(CancellationResponse.from(found, bookingId,
                        found.dischargeLocation().orElse(null))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** キャンセルを申請する（US30-1）。**営業担当者のみ**。 */
    @PostMapping("/api/v1/bookings/{bookingId}/cancellation")
    public ResponseEntity<CancellationOutcomeResponse> request(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId,
            @RequestBody CancellationRequests.RequestCancellationRequest body) {
        requireSales(userId, roles);

        CancellationOutcome outcome = request.request(bookingId, body.reason(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CancellationOutcomeResponse(
                CancellationResponse.from(outcome.request(), bookingId, null),
                outcome.awaitingApproval()));
    }

    /** 承認する（US30-5）。**追跡管理者のみ**。陸揚げ地は候補に限る。 */
    @PutMapping("/api/v1/bookings/{bookingId}/cancellation/approve")
    public CancellationResponse approve(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId,
            @RequestBody CancellationRequests.ApproveCancellationRequest body) {
        requireTracker(userId, roles);

        CancellationRequest approved = decide.approve(bookingId,
                body.dischargeLocationUnLocode(), userId, body.decisionReason());
        return CancellationResponse.from(approved, bookingId,
                approved.dischargeLocation().orElse(null));
    }

    /** 却下する（US30-7）。**追跡管理者のみ**。予約は輸送中のまま維持される。 */
    @PutMapping("/api/v1/bookings/{bookingId}/cancellation/reject")
    public CancellationResponse reject(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId,
            @RequestBody CancellationRequests.RejectCancellationRequest body) {
        requireTracker(userId, roles);

        return CancellationResponse.from(
                decide.reject(bookingId, userId, body.decisionReason()), bookingId, null);
    }

    private java.util.Optional<PendingCancellationResponse> toPending(CancellationRequest found) {
        return cargoes.findById(found.cargoId())
                .map(cargo -> PendingCancellationResponse.from(found, cargo));
    }

    /**
     * 申請するのは営業担当者である。
     *
     * <p>荷主から「止めてほしい」と言われるのは営業であり、追跡管理者ではない。
     */
    private void requireSales(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SALES)) {
            throw forbidden();
        }
    }

    /**
     * 承認するのは追跡管理者である（US30-4）。
     *
     * <p><strong>営業には開かない。</strong>自分の申請を自分で承認できると、承認の意味が
     * 無くなる。
     */
    private void requireTracker(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_TRACKER)) {
            throw forbidden();
        }
    }

    /** 申請の行方は、申請した営業も承認する追跡管理者も読む。 */
    private void requireSalesOrTracker(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SALES, Role.ROLE_TRACKER)) {
            throw forbidden();
        }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
    }

    /** 入力の誤り。**理由を返さないと、利用者は何を直せばよいか分からない**。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * 業務の規則に触れた。
     *
     * <p><strong>400 では返さない。</strong>入力が誤っているのではなく、いまはその操作を
     * 行えない、という意味である。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
}
