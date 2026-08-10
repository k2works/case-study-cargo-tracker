package com.example.cargotracker.billing.infrastructure.repositories;

import java.time.Instant;

/** {@code invoice_reminder} の 1 行（IT14 レビュー C3）。 */
public class ReminderRecord {

    private String invoiceNumber;
    private Instant remindedAt;
    private String remindedBy;
    private String note;

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(Instant remindedAt) {
        this.remindedAt = remindedAt;
    }

    public String getRemindedBy() {
        return remindedBy;
    }

    public void setRemindedBy(String remindedBy) {
        this.remindedBy = remindedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
