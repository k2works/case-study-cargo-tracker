package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.application.InvoiceCalculation;
import com.example.cargotracker.billing.domain.model.commands.AdjustInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.IssueInvoiceCommand;
import com.example.cargotracker.billing.domain.model.commands.RecordPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.ReverseAdjustmentCommand;
import com.example.cargotracker.billing.domain.model.commands.VoidInvoiceCommand;
import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindOverdueInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceListView;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceView;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import java.time.Clock;
import java.util.List;
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
 * <p><b>算出を手で始める入口は置かない。</b> 算出は引取（{@code CargoDeliveredEvent}）
 * の連鎖で始まる。手で始める入口を作ると、<b>連鎖が止まっていることに気づかないまま
 * 手で回してしまう</b>。</p>
 *
 * <p><b>作り直す入口だけは置く</b>（IT14 引き継ぎ B）。材料が足りずに作れなかった
 * 予約は要確認一覧に出ているが、材料を直したあとに請求へ戻す道が無く、<b>締めの
 * 母集団から落ち続けていた</b>。作り直せるのは<b>要確認に出ている予約だけ</b>に
 * 限る——それ以外を受け付けると、結局「手で始める入口」になる。</p>
 *
 * <p><b>荷主向けの請求書（S62）はここに無い。</b> 荷主が読む請求書は発行
 * （{@code INVOICED}）が前提で、それは US23（IT14）である。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
public class InvoiceController {

    /** 作り直しの対象になる要確認の種別。連鎖が請求書を作れなかった記録である。 */
    private static final String REACTION_FAILED = "REACTION_FAILED";

    private final CommandGateway commands;
    private final QueryDispatcher queries;
    private final InvoiceCalculation calculation;
    private final AttentionItemMapper attentionItems;
    private final Clock clock;

    public InvoiceController(CommandGateway commands, QueryDispatcher queries,
            InvoiceCalculation calculation, AttentionItemMapper attentionItems, Clock clock) {
        this.commands = commands;
        this.queries = queries;
        this.calculation = calculation;
        this.attentionItems = attentionItems;
        this.clock = clock;
    }

    /**
     * 一覧（S60）。**既定で入金済・取消を外す。**
     *
     * <p>{@code overdue=true} で<b>未払いだけ</b>に絞る（US23 §受入基準 5）。
     * 絞りはサーバが数える——全件を読んでから画面で数えると、上限の打ち切りで
     * 未払いが漏れる。</p>
     */
    @GetMapping
    public ResponseEntity<InvoiceListView> list(
            @RequestParam(defaultValue = "false") boolean includeSettled,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) String bookingId) {
        if (overdue) {
            // **今日はサーバが決める。** 業務タイムゾーンで判断しないと、
            // 時差の分だけ 1 日早く督促が飛ぶ時間帯ができる。
            return ResponseEntity.ok(queries.query(
                    new FindOverdueInvoicesQuery(null), InvoiceListView.class));
        }
        return ResponseEntity.ok(queries.query(
                new FindInvoicesQuery(includeSettled, bookingId), InvoiceListView.class));
    }

    /**
     * 請求書を発行する（US23 §受入基準 1）。
     *
     * <p><b>支払期限は返さない値ではなく、集約が決める</b>（不変条件 3）。
     * 画面は発行後に読み直す。</p>
     */
    @PostMapping("/{invoiceId}/issue")
    public ResponseEntity<Void> issue(@PathVariable String invoiceId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username) {
        commands.sendAndWait(new IssueInvoiceCommand(invoiceId, username), Void.class);
        return ResponseEntity.ok().build();
    }

    /**
     * 入金を記録する（US23 §受入基準 3・4）。
     *
     * <p><b>決済機関との接続はスコープ外</b>（計画の注 N9）。経理担当者が入金
     * 明細を見て記録する。</p>
     *
     * <p><b>識別子はサーバで採る。</b> 画面に採らせると、押し直しが二重の入金に
     * なる。<b>36 文字に収める</b>——列は {@code VARCHAR(36)} で、接頭辞 + UUID を
     * そのまま繋ぐとあふれ、投影だけが静かに退避される。</p>
     */
    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<Void> recordPayment(@PathVariable String invoiceId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody RecordPaymentRequest request) {
        String paymentId = "PAY-"
                + java.util.UUID.randomUUID().toString().replace("-", "");
        commands.sendAndWait(new RecordPaymentCommand(invoiceId, paymentId,
                request.amount(), request.paidAt(), username), Void.class);
        return ResponseEntity.ok().build();
    }

    /**
     * 入金の入力。
     *
     * @param paidAt <b>入金のあった時刻</b>（記録した時刻ではない）。記録は
     *     後日になることがある
     */
    public record RecordPaymentRequest(
            @NotNull BigDecimal amount,
            @NotNull java.time.Instant paidAt) {
    }

    /**
     * 請求書を取り消す（UC18）。
     *
     * <p><b>理由は必須。</b> 取り消した請求書は荷主にも見えなくなるので、
     * 何が起きたかを追えなければ、あとから誰も確かめられない。</p>
     */
    @PostMapping("/{invoiceId}/void")
    public ResponseEntity<Void> voidInvoice(@PathVariable String invoiceId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody VoidRequest request) {
        commands.sendAndWait(new VoidInvoiceCommand(invoiceId, request.reason(), username),
                Void.class);
        return ResponseEntity.ok().build();
    }

    /** 取消の入力。 */
    public record VoidRequest(@NotBlank String reason) {
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
    public ResponseEntity<AdjustedView> adjust(@PathVariable String invoiceId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody AdjustRequest request) {
        // **識別子はサーバで採る。** 画面に採らせると、送り直しのたびに新しい
        // 調整が積まれる（押し直しが二重の調整になる）。
        //
        // **36 文字に収める。** 列は VARCHAR(36) で、"ADJ-" + UUID は 40 文字に
        // なる——集約は受け付けるので、投影が退避されるまで気づけない
        // （IT13 の請求書 ID がまったく同じ形で踏んだ）。ハイフンを外すと
        // 4 + 32 = 36 でちょうど収まり、entropy も落ちない。
        String adjustmentId = "ADJ-"
                + java.util.UUID.randomUUID().toString().replace("-", "");
        commands.sendAndWait(new AdjustInvoiceCommand(invoiceId, adjustmentId, request.amount(),
                request.reason(), request.basisExceptionId(), username), Void.class);
        return ResponseEntity.ok(new AdjustedView(adjustmentId));
    }

    /** 入れた調整。<b>識別子を返す</b>——取り消すときの宛先になる。 */
    public record AdjustedView(String adjustmentId) {
    }

    /**
     * 調整を取り消す（IT14 引き継ぎ C）。
     *
     * <p><b>誤入力は起きる。</b> 符号を取り違えた調整が入ったまま請求書を発行
     * すると、荷主に誤った額を請求することになる。発行（US23）を足す前に、
     * 戻せる道を作っておく。</p>
     *
     * <p><b>消さずに反対向きを積む。</b> 何が起きたかを追えない記録は、経理に
     * とって根拠にならない。</p>
     */
    @PostMapping("/{invoiceId}/adjustments/{adjustmentId}/reversal")
    public ResponseEntity<Void> reverseAdjustment(@PathVariable String invoiceId,
            @PathVariable String adjustmentId,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody ReverseRequest request) {
        commands.sendAndWait(new ReverseAdjustmentCommand(invoiceId, adjustmentId,
                request.reason(), username), Void.class);
        return ResponseEntity.ok().build();
    }

    /** 取り消しの入力。<b>理由は必須</b>——理由の読めない取り消しを残さない。 */
    public record ReverseRequest(@NotBlank String reason) {
    }

    /**
     * 作れなかった請求を作り直す（IT14 引き継ぎ B）。
     *
     * <p><b>要確認に出ている予約だけ</b>を受け付ける。重量や区間が届いていな
     * かった予約は、材料が直っても誰も請求へ戻さないままだった——月末の締めで
     * 数が合わないのに、どこから落ちたのかが分からない。</p>
     *
     * <p><b>材料の判定は連鎖と同じものを使う</b>（{@link InvoiceCalculation}）。
     * ここで書き直すと、手の入口だけが正しくて連鎖が誤りを素通りさせる、または
     * その逆が起きる。</p>
     *
     * <p><b>作れたら要確認を確認済にする。</b> 残したままにすると、片づいた
     * はずのものが毎朝の一覧に出続ける。</p>
     */
    @PostMapping("/recalculate")
    public ResponseEntity<RecalculatedView> recalculate(
            @RequestHeader(value = "X-Auth-Roles", required = false) String roles,
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody RecalculateRequest request) {
        String attentionItemId = openAttentionItemFor(request.bookingId());
        if (attentionItemId == null) {
            // **共有の対応表が扱う型で投げる。** ResponseStatusException の理由は
            // 既定の応答本文に載らず、画面には「作れませんでした」しか届かない。
            throw new IllegalTransition("予約 " + request.bookingId()
                    + " は要確認に出ていません（連鎖が止まっていないか確かめてください）");
        }

        switch (calculation.prepareForBooking(request.bookingId())) {
            case InvoiceCalculation.Outcome.AlreadyInvoiced already ->
                    throw new IllegalTransition("予約 " + request.bookingId()
                            + " にはすでに有効な請求書があります: " + already.invoiceId());
            case InvoiceCalculation.Outcome.Blocked blocked ->
                    // **理由をそのまま返す。** 「作れません」だけでは、何を直せば
                    // よいのかが分からず、同じ操作が繰り返される。
                    throw new IllegalTransition(blocked.reason());
            case InvoiceCalculation.Outcome.Ready ready -> {
                String invoiceId = commands.sendAndWait(ready.command(), String.class);
                attentionItems.acknowledge(attentionItemId, rolesOf(roles),
                        username == null || username.isBlank() ? "system" : username,
                        clock.instant());
                return ResponseEntity.ok(new RecalculatedView(invoiceId));
            }
        }
    }

    /** その予約について、まだ確認されていない「請求を作れなかった」記録。 */
    private String openAttentionItemFor(String bookingId) {
        return attentionItems.findOpenByRole("ROLE_ACCOUNTANT").stream()
                .filter(row -> REACTION_FAILED.equals(row.kind()))
                .filter(row -> "BOOKING".equals(row.targetType()))
                .filter(row -> bookingId.equals(row.targetId()))
                .map(AttentionItemMapper.AttentionItemRow::itemId)
                .findFirst()
                .orElse(null);
    }

    private static List<String> rolesOf(String header) {
        if (header == null || header.isBlank()) {
            return List.of("ROLE_ACCOUNTANT");
        }
        return java.util.Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }

    /** 作り直しの入力。<b>予約だけ</b>——追跡番号は貨物の写しから引く。 */
    public record RecalculateRequest(@NotBlank String bookingId) {
    }

    /** 作り直した結果。画面はこの請求書へ移る。 */
    public record RecalculatedView(String invoiceId) {
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
