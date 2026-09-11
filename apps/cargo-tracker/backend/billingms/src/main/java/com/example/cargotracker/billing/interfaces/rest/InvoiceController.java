package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.domain.model.commands.AdjustInvoiceCommand;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceListView;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceView;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 請求（S60・S61 / UC17・US21・US22）。
 *
 * <p><b>算出の入口は置かない。</b> 算出は引取（{@code CargoDeliveredEvent}）の連鎖で
 * 始まる。手で始める入口を先に作ると、<b>連鎖が止まっていることに気づかないまま
 * 手で回してしまう</b>——止まっていることは要確認一覧に出る。</p>
 *
 * <p><b>荷主向けの請求書（S62）はここに無い。</b> 荷主が読む請求書は発行
 * （{@code INVOICED}）が前提で、それは US23（IT14）である。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
public class InvoiceController {

    private final CommandGateway commands;
    private final QueryDispatcher queries;

    public InvoiceController(CommandGateway commands, QueryDispatcher queries) {
        this.commands = commands;
        this.queries = queries;
    }

    /** 一覧（S60）。**既定で入金済・取消を外す。** */
    @GetMapping
    public ResponseEntity<InvoiceListView> list(
            @RequestParam(defaultValue = "false") boolean includeSettled,
            @RequestParam(required = false) String bookingId) {
        return ResponseEntity.ok(queries.query(
                new FindInvoicesQuery(includeSettled, bookingId), InvoiceListView.class));
    }

    /** 請求書 1 通（S61）。 */
    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceView> find(@PathVariable String invoiceId) {
        InvoiceView view = queries.query(new FindInvoiceQuery(invoiceId), InvoiceView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /**
     * その予約の有効な請求書（S22 予約詳細から飛ぶ）。
     *
     * <p><b>無ければ 404。</b> 「まだ算出されていない」と「取り消された」は
     * 画面では同じに見えるが、どちらも開く先が無い。</p>
     */
    @GetMapping("/by-booking/{bookingId}")
    public ResponseEntity<InvoiceView> findByBooking(@PathVariable String bookingId) {
        InvoiceView view = queries.query(new FindInvoiceOfBookingQuery(bookingId),
                InvoiceView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /**
     * 料金を調整する（US21 §受入基準 6）。
     *
     * <p><b>根拠の例外を受け取る。</b> 減額も補償費用も「なぜその額か」が読めな
     * ければ、あとから誰も確かめられない。</p>
     */
    @PostMapping("/{invoiceId}/adjustments")
    public ResponseEntity<Void> adjust(@PathVariable String invoiceId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody AdjustRequest request) {
        commands.sendAndWait(new AdjustInvoiceCommand(invoiceId, request.amount(),
                request.reason(), request.basisExceptionId(), username), Void.class);
        return ResponseEntity.ok().build();
    }

    /**
     * 調整の入力。
     *
     * @param amount 調整額。<b>符号で向きを表す</b>——減額は負、補償費用は正
     * @param basisExceptionId 根拠になった例外 ID（任意。留置は申告番号）
     */
    public record AdjustRequest(
            @NotNull BigDecimal amount,
            @NotBlank String reason,
            String basisExceptionId) {
    }
}
