package com.example.cargotracker.billing.domain.model.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 請求書を発行した（billingms の内部イベント / US23 §受入基準 1）。
 *
 * <p><b>支払期限を載せる。</b> 投影はコマンドも他のイベントも読まずにこの 1 本で
 * 行を更新する。集約が数えた期限（発行日 + 30 日）をそのまま運ばないと、投影が
 * 自分で数え直すことになり、規則が 2 か所に書かれる。</p>
 *
 * <p><b>金額も載せる。</b> 荷主が S62 で読む請求書は「発行した時点の額」で、
 * 発行後に調整が入ることはない（発行すると調整を受け付けない）。</p>
 *
 * <p><b>通知の記録もここから作る</b>（US23 §受入基準 2）。送信基盤はスコープ外
 * なので、残すのは「いつ・誰に・何を伝えたか」である。</p>
 */
public record InvoiceIssuedEvent(
        @EventTag(key = "invoiceId") String invoiceId,
        String bookingId,
        String shipperId,
        BigDecimal totalAmount,
        String currency,
        LocalDate issuedOn,
        LocalDate dueOn,
        String issuedBy,
        Instant issuedAt) {
}
