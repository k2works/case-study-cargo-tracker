package com.example.billingms.interfaces.rest;

import com.example.billingms.application.internal.commandservices.AdjustmentCommand;
import com.example.billingms.application.internal.commandservices.AlreadyInvoicedException;
import com.example.billingms.application.internal.commandservices.BillingNotAvailableException;
import com.example.billingms.application.internal.commandservices.CalculateChargeUseCase;
import com.example.billingms.application.internal.commandservices.InvoiceNotFoundException;
import com.example.billingms.application.internal.commandservices.PaymentCommand;
import com.example.billingms.application.internal.commandservices.SettleInvoiceUseCase;
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
    private final SettleInvoiceUseCase settlement;

    public BillingController(CalculateChargeUseCase calculateCharge, InvoiceRepository invoices,
            SettleInvoiceUseCase settlement) {
        this.calculateCharge = calculateCharge;
        this.invoices = invoices;
        this.settlement = settlement;
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

    /**
     * 予約サービス（bookingms）に届かないときの応答（IT11 レビュー 中・xp-architect）。
     *
     * <p><strong>500 にしない。</strong>経理担当者には「一覧が壊れた」としか見えず、
     * 待てば直るのか自分の操作が悪いのかが分からない。<strong>相手に届いていない</strong>
     * ことを言う。
     */
    @org.springframework.web.bind.annotation.ExceptionHandler({
            org.springframework.web.client.ResourceAccessException.class,
            org.springframework.web.client.HttpServerErrorException.class})
    public ResponseEntity<java.util.Map<String, String>> bookingServiceUnavailable(
            Exception error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(java.util.Map.of("message",
                        "予約サービスに接続できないため、料金の情報を取得できません。"
                                + "しばらく待って開き直してください。"));
    }

    /**
     * 入金を確認する（受入基準 23-3・23-4）。
     *
     * <p><strong>決済機関とは連携していない</strong>（代替）。経理担当者が通帳や入金明細を
     * 見て入れる。入れた根拠（入金日・金額・方法・参照番号）は残る。
     */
    @PostMapping("/invoices/{invoiceNumber}/payment")
    public InvoiceResponse confirmPayment(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String invoiceNumber,
            @RequestBody ConfirmPaymentRequest request) {
        requireAccountant(userId, roles);

        try {
            return InvoiceResponse.from(settlement.confirmPayment(invoiceNumber,
                    new PaymentCommand(request.amountValue(), request.paidAt(),
                            request.method(), request.transactionReference())));
        } catch (InvoiceNotFoundException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage());
        } catch (IllegalStateException conflict) {
            // すでに入金済・取り消し済み。**待っても変わらない**
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    /**
     * 請求書を取り消す（赤伝・[ADR-028] 決定 3）。
     *
     * <p><strong>理由は必須である。</strong>なぜ取り消したかが残らないと、あとから見て
     * 「二重発行の失敗」と区別できない。
     */
    @PostMapping("/invoices/{invoiceNumber}/void")
    public InvoiceResponse revoke(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String invoiceNumber,
            @RequestBody VoidInvoiceRequest request) {
        requireAccountant(userId, roles);

        try {
            return InvoiceResponse.from(settlement.revoke(invoiceNumber, request.reason()));
        } catch (InvoiceNotFoundException notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound.getMessage());
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    /**
     * 支払期限を過ぎた請求書（受入基準 23-5 の代替）。
     *
     * <p><strong>未払い通知のメールは無い。</strong>経理担当者はこの一覧でしか気づけない
     * ——件数を出すだけでは仕事は進まないので、対象そのものを返す。
     */
    @GetMapping("/invoices/overdue")
    public List<InvoiceResponse> overdue(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAccountant(userId, roles);

        return settlement.overdue().stream().map(InvoiceResponse::from).toList();
    }

    private void requireAccountant(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ACCOUNTANT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
