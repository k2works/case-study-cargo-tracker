package com.example.routingms.infrastructure.persistence.record;

import java.time.ZonedDateTime;

/**
 * voyage テーブルの MyBatis マッピング用レコードクラス
 */
public class VoyageRecord {

    private Long id;
    private String voyageNumber;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public VoyageRecord() {}

    public VoyageRecord(Long id, String voyageNumber, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        this.id = id;
        this.voyageNumber = voyageNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }
}
