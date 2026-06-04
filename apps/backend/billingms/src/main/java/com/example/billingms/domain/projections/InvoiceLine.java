package com.example.billingms.domain.projections;

import java.math.BigDecimal;

/**
 * 請求書明細 Read Model（invoice_line テーブル、IT7 US21 / US22）。
 *
 * <p>data-model.md L713-721 の `invoice_line` テーブル定義に対応。line_type 駆動設計（ADR-0015）:</p>
 *
 * <ul>
 *   <li>BASIC: 基本料金（InvoiceCalculatedEvent 受信時に投影）</li>
 *   <li>DISCOUNT: 法人割引（DiscountAppliedEvent、負値、US22）</li>
 *   <li>ADJUSTMENT: 例外時補償（InvoiceAdjustedEvent、US21 例外調整）</li>
 *   <li>SURCHARGE: 割増料金（IT8 拡張用）</li>
 * </ul>
 */
public class InvoiceLine {

    private String invoiceId;
    private int lineSeq;
    private String lineType;
    private String description;
    private BigDecimal amount;
    private String reasonCode;

    public InvoiceLine() { /* MyBatis result mapping */ }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public int getLineSeq() { return lineSeq; }
    public void setLineSeq(int lineSeq) { this.lineSeq = lineSeq; }

    public String getLineType() { return lineType; }
    public void setLineType(String lineType) { this.lineType = lineType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
}
