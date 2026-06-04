package com.example.billingms.interfaces.events;

import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.model.BillingStatus;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceLineMapper;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * billingms 内部の Invoice 投影 EventHandler（US21 / IT7 タスク 2.5）。
 *
 * <p>Invoice 集約のイベント（InvoiceCalculatedEvent / DiscountAppliedEvent / etc.）を受信して
 * {@code invoice} / {@code invoice_line} テーブルに投影する。{@code @ProcessingGroup("local-billing")}
 * は ADR-0014/0016 命名規約準拠（local- prefix）で subscribing モード。</p>
 *
 * <p>本 EventHandler は IT7 タスク 2.5 で InvoiceCalculatedEvent → invoice 行 + BASIC 行のみ実装。
 * DiscountAppliedEvent → DISCOUNT 行は Task 3.x、InvoiceAdjustedEvent → ADJUSTMENT 行は Task 4.x で
 * 順次追加する。</p>
 */
@Component
@ProcessingGroup("local-billing")
public class InvoiceProjectionsEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProjectionsEventHandler.class);

    private final InvoiceSummaryMapper summaryMapper;
    private final InvoiceLineMapper lineMapper;

    public InvoiceProjectionsEventHandler(InvoiceSummaryMapper summaryMapper,
                                          InvoiceLineMapper lineMapper) {
        this.summaryMapper = summaryMapper;
        this.lineMapper = lineMapper;
    }

    @EventHandler
    public void on(InvoiceCalculatedEvent event) {
        summaryMapper.insertInvoice(
                event.invoiceId(),
                event.bookingId(),
                event.shipperId(),
                event.basicAmount(),
                event.currency(),
                BillingStatus.CALCULATED.name()
        );
        lineMapper.insertInvoiceLine(
                event.invoiceId(),
                1,
                "BASIC",
                "基本料金（重量 × 距離 × 種別係数 + 取扱費）",
                event.basicAmount(),
                null
        );
        log.info("[local-billing] Invoice 投影 invoiceId={} bookingId={} basicAmount={}",
                event.invoiceId(), event.bookingId(), event.basicAmount());
    }
}
