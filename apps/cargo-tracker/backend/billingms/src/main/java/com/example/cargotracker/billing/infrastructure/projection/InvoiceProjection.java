package com.example.cargotracker.billing.infrastructure.projection;

import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.valueobjects.BillingStatus;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.infrastructure.persistence.InvoiceMapper;
import java.time.Clock;
import org.axonframework.messaging.core.annotation.MessageIdentifier;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 請求書の投影（US21 §受入基準 5）。
 *
 * <p><b>投影はコマンドを送らない。</b> 送るとリプレイのたびに副作用が再実行される
 * （[ADR-0001] 決定 6）。</p>
 *
 * <p><b>不変条件 2 の三段目をここで書く。</b> 「有効な請求書は予約ごとに 1 通」は
 * 集約では守れない（1 請求書 1 集約なので他の請求書を知らない）。DB の部分ユニークが
 * 弾いたら、<b>弾いた事実を要確認一覧に残す</b>——集約は受け付けているので、
 * 記録しなければ「作ったのに一覧に出ない」が誰にも見えない。</p>
 *
 * <p><b>明細は入れ直す。</b> 追記専用の行はリプレイで増える（IT6 で実際に踏んだ）。
 * ただし<b>調整行は消さない</b>——調整は別のイベントで積まれるので、算出の
 * 読み直しで消すと調整が失われる。</p>
 *
 * <p><b>処理の列を請求書ごとに分ける</b>（[ADR-0014] 決定 4）。</p>
 */
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = "invoiceId")
@Component
public class InvoiceProjection {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProjection.class);

    /** 弾いた事実の宛先。<b>請求の失敗は経理</b>（ui_design.md の要確認一覧の規約）。 */
    private static final String ACCOUNTANT = "ROLE_ACCOUNTANT";

    private final InvoiceMapper invoices;
    private final AttentionItemRecorder attentionItems;
    private final Clock clock;

    public InvoiceProjection(InvoiceMapper invoices, AttentionItemRecorder attentionItems,
            Clock clock) {
        this.invoices = invoices;
        this.attentionItems = attentionItems;
        this.clock = clock;
    }

    @EventHandler
    public void on(InvoiceCalculatedEvent event, @MessageIdentifier String eventId) {
        int inserted = invoices.insert(new InvoiceMapper.InvoiceRow(
                event.invoiceId(), event.bookingId(), event.shipperId(), event.shipperName(),
                event.shipperType(), event.contractNumber(), event.baseAmount(),
                event.discountAmount(), java.math.BigDecimal.ZERO, event.taxAmount(),
                event.totalAmount(), event.currency(), event.discountRate(),
                BillingStatus.CALCULATED.name(), event.calculatedAt(), clock.instant(), eventId));

        if (inserted == 0 && invoices.find(event.invoiceId()) == null) {
            recordRejection(event);
            return;
        }

        invoices.deleteCalculatedLineItems(event.invoiceId());
        int seq = 1;
        for (InvoiceCalculatedEvent.LineItem item : event.lineItems()) {
            invoices.insertLineItem(new InvoiceMapper.LineItemRow(event.invoiceId(), seq++,
                    item.itemType(), item.description(), item.amount(), item.currency(),
                    item.basisExceptionId(),
                    // 算出の明細は消して入れ直すので、元イベントで縛らない。
                    null));
        }
    }

    @EventHandler
    public void on(InvoiceAdjustedEvent event, @MessageIdentifier String eventId) {
        if (invoices.find(event.invoiceId()) == null) {
            // 算出が弾かれた請求書の調整。書く先が無いので見送る——**黙っては
            // 見送らない**（弾いた事実は算出のときに要確認へ出している）。
            log.warn("請求書 {} が読み取りモデルに無いので調整を写せません", event.invoiceId());
            return;
        }
        invoices.updateAmounts(event.invoiceId(), event.adjustmentTotal(), event.taxAmount(),
                event.totalAmount(), clock.instant(), eventId);

        // **調整行は積む。** 減額も補償費用も、あとから「なぜその額か」を
        // 追えなければ根拠にならない（US28 §8 の受け側）。
        // **元イベントの識別子で縛る。** 調整は入れ直せない（別のイベントで積む）
        // ので、2 度届くと MAX(line_seq)+1 が新しい番号を採って同じ行が増える。
        invoices.insertLineItem(new InvoiceMapper.LineItemRow(event.invoiceId(),
                invoices.nextLineSeq(event.invoiceId()), LineItemType.ADJUSTMENT.name(),
                event.reason(), event.amount(), event.currency(), event.basisExceptionId(),
                eventId));
    }

    /**
     * 弾いた事実を残す（不変条件 2 の三段目）。
     *
     * <p><b>宛先は経理。</b> 全員に見えるものは誰も直さない。</p>
     */
    private void recordRejection(InvoiceCalculatedEvent event) {
        var existing = invoices.findActiveByBooking(event.bookingId());
        String reason = "予約 " + event.bookingId() + " には有効な請求書（"
                + (existing == null ? "不明" : existing.invoiceId()) + "）が既にあります";
        log.warn("請求書 {} を投影できませんでした: {}", event.invoiceId(), reason);
        attentionItems.add("PROJECTION_REJECTED", "INVOICE", event.invoiceId(), ACCOUNTANT,
                reason, null, clock.instant());
    }
}
