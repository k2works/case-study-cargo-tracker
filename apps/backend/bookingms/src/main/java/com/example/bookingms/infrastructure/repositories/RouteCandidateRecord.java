package com.example.bookingms.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * route_candidate テーブルの永続化レコード
 */
public class RouteCandidateRecord {

    private Long id;
    private Long estimateId;
    private String voyageNumber;
    private String transitPort;
    private int transitDays;
    private BigDecimal estimatedCost;
    private int rank;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEstimateId() { return estimateId; }
    public void setEstimateId(Long estimateId) { this.estimateId = estimateId; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getTransitPort() { return transitPort; }
    public void setTransitPort(String transitPort) { this.transitPort = transitPort; }

    public int getTransitDays() { return transitDays; }
    public void setTransitDays(int transitDays) { this.transitDays = transitDays; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
