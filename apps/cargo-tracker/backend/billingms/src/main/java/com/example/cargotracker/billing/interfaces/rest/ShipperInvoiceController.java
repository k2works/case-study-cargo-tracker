package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindShipperInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceView;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 荷主が読む自社の請求書（S62 / US23 §受入基準 2）。
 *
 * <p><b>金額を出す唯一の荷主向け画面である</b>（{@code ui_design.md}）。
 * 経理向けの {@code /invoices} とは別の経路にする——同じ経路にロールで分岐を
 * 足すと、載せ忘れた分岐ほど無防備になる。</p>
 *
 * <p><b>荷主 ID はクライアントの指定を信じない。</b> Gateway が JWT から
 * 取り出して {@code X-Auth-Shipper-Id} で伝える（[ADR-0001] 決定 4）。
 * クエリパラメータで受け取ると、他社の請求書を読まれる。</p>
 *
 * <p><b>発行済と入金済だけを出す。</b> 算出済は社内の途中経過で、荷主に見せる
 * ものではない——「まだ確定していない金額」を見せると、その額で会話が始まる。
 * 取消も出さない（一度取り消したものを見せ続けない）。</p>
 *
 * <p><b>送信基盤はスコープ外</b>（計画の注 N9）。荷主はメールではなくこの画面で
 * 請求書を読む。通知の記録（いつ・何を伝えたか）は {@code invoice_notification}
 * に残る。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/shipper-invoices")
public class ShipperInvoiceController {

    private final QueryDispatcher queries;

    public ShipperInvoiceController(QueryDispatcher queries) {
        this.queries = queries;
    }

    /**
     * 自社の請求書 1 通。
     *
     * <p><b>他社の請求書は 404。</b> 403 にすると「その請求書は存在する」ことを
     * 教えてしまう。</p>
     */
    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceView> find(@PathVariable String invoiceId,
            @RequestHeader(name = "X-Auth-Shipper-Id", required = false) String shipperId) {
        if (shipperId == null || shipperId.isBlank()) {
            // 荷主が伝わっていなければ何も出さない。既定を置くと、伝達が壊れて
            // いることに気づかないまま他社の請求書が見える。
            return ResponseEntity.notFound().build();
        }
        InvoiceView view = queries.query(new FindShipperInvoiceQuery(invoiceId, shipperId),
                InvoiceView.class);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }
}
