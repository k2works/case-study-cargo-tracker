package com.example.billingms.interfaces.rest;

import com.example.billingms.application.internal.AdjustmentCommand;
import com.example.billingms.application.internal.AlreadyInvoicedException;
import com.example.billingms.application.internal.BillingNotAvailableException;
import com.example.billingms.application.internal.CalculateChargeUseCase;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 精算（US21・US22・UC17）。
 *
 * <p><strong>経理担当者だけが使う。</strong>請求の金額を決めるのは経理であり、営業や
 * 経路設計者とは職掌が違う。<strong>画面に出す・出さないでは守れない</strong>ため、
 * ここでも同じ規則を持つ。
 *
 * <p><strong>認可を入力検証より先に置く。</strong>{@code @Valid} が先に走ると、権限の
 * 無い相手に入力仕様を教えることになる。
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final CalculateChargeUseCase calculateCharge;
    private final InvoiceRepository invoices;

    public BillingController(CalculateChargeUseCase calculateCharge, InvoiceRepository invoices) {
        this.calculateCharge = calculateCharge;
        this.invoices = invoices;
    }

    /**
     * 料金を算出していない引取済・キャンセル済みの予約（US21-1）。
     *
     * <p><strong>経理担当者が仕事を始める場所である。</strong>他に気づく手段は無い
     * （通知の仕組みは US23 以降）。
     */
    @GetMapping("/unbilled")
    public List<UnbilledBookingResponse> unbilled(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAccountant(userId, roles);

        return calculateCharge.billable().stream()
                .map(UnbilledBookingResponse::from)
                .toList();
    }

    /** 発行済みの精算書の一覧。 */
    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAccountant(userId, roles);

        return invoices.findAll().stream().map(InvoiceResponse::from).toList();
    }

    /** 発行済みの精算書 1 件。 */
    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse invoice(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String invoiceId) {
        requireAccountant(userId, roles);

        return invoices.findById(invoiceId)
                .map(InvoiceResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "精算書が見つかりません"));
    }

    /**
     * 料金の算出結果（[ADR-027] 決定 3）。
     *
     * <p><strong>保存しない。</strong>毎回計算して返す。
     */
    @GetMapping("/calculations/{bookingId}")
    public ChargeCalculationResponse calculation(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId) {
        requireAccountant(userId, roles);

        try {
            return ChargeCalculationResponse.from(calculateCharge.calculate(bookingId));
        } catch (BillingNotAvailableException | AlreadyInvoicedException error) {
            // **409 で返す。** 断られた理由が利用者に伝わる形にする
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
        }
    }

    /**
     * 料金を確定して精算書を発行する（US21-4・US21-5）。
     *
     * <p><strong>調整はここでまとめて受ける</strong>（決定 3）。
     */
    @PostMapping("/{bookingId}/calculate")
    public ResponseEntity<InvoiceResponse> calculate(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String bookingId,
            @RequestBody CalculateChargeRequest request) {
        requireAccountant(userId, roles);

        List<AdjustmentCommand> adjustments = request.adjustments() == null ? List.of()
                : request.adjustments().stream()
                        .map(item -> new AdjustmentCommand(item.description(),
                                item.amountValue()))
                        .toList();

        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(InvoiceResponse.from(calculateCharge.confirm(bookingId, adjustments)));
        } catch (BillingNotAvailableException | AlreadyInvoicedException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage());
        } catch (IllegalArgumentException error) {
            // 根拠の無い調整（決定 6）。**利用者の入力の誤りであり 400 である**
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    private void requireAccountant(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ACCOUNTANT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
