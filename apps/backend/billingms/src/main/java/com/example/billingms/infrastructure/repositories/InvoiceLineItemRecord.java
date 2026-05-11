package com.example.billingms.infrastructure.repositories;

import java.time.LocalDateTime;

/**
 * invoice_line_item テーブルの永続化レコード
 */
public class InvoiceLineItemRecord {

    private Long id;
    private Long invoiceId;
    private String description;
    private Long amountValue;
    private String amountCurrency;
    private int seqNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getAmountValue() { return amountValue; }
    public void setAmountValue(Long amountValue) { this.amountValue = amountValue; }

    public String getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(String amountCurrency) { this.amountCurrency = amountCurrency; }

    public int getSeqNumber() { return seqNumber; }
    public void setSeqNumber(int seqNumber) { this.seqNumber = seqNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
