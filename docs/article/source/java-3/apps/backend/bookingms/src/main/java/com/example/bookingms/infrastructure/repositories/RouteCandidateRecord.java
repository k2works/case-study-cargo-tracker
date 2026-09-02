package com.example.bookingms.infrastructure.repositories;

import java.math.BigDecimal;

/** ルート候補の行（受入基準 01-3）。 */
public class RouteCandidateRecord {

    private Long estimateId;

    private String voyageNumber;

    private String transitPort;

    private int transitDays;

    private BigDecimal estimatedCost;

    /** 推奨順。**順序に意味がある**——上から見せる。 */
    private int rank;

    public Long getEstimateId() {
        return estimateId;
    }

    public void setEstimateId(Long estimateId) {
        this.estimateId = estimateId;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getTransitPort() {
        return transitPort;
    }

    public void setTransitPort(String transitPort) {
        this.transitPort = transitPort;
    }

    public int getTransitDays() {
        return transitDays;
    }

    public void setTransitDays(int transitDays) {
        this.transitDays = transitDays;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }
}
