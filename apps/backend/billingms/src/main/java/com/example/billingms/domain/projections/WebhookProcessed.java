package com.example.billingms.domain.projections;

import java.time.LocalDateTime;

/**
 * Stripe webhook 冪等性管理 Read Model（IT9 A1.2 / ADR-0020 / US26）。
 *
 * <p>{@code webhook_processed} テーブルの 1 行を表す。eventId（Stripe Event ID）を PK とし、
 * 同一 event の再送を 200 で受理しつつ副作用を起こさない冪等性ガードに使う。</p>
 */
public class WebhookProcessed {

    private String eventId;
    private String provider;
    private String eventType;
    private String invoiceId;
    private String payloadHash;
    private String processingStatus;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private String errorReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WebhookProcessed() { /* MyBatis result mapping */ }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
