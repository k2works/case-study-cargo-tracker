package com.example.bookingms.domain.model.entities;

import java.math.BigDecimal;

/**
 * ルート候補エンティティ
 */
public class RouteCandidate {

    private Long id;
    private final String voyageNumber;
    private final String transitPort;
    private final int transitDays;
    private final BigDecimal estimatedCost;
    private final int rank;

    public RouteCandidate(String voyageNumber, String transitPort,
                           int transitDays, BigDecimal estimatedCost, int rank) {
        this.voyageNumber = voyageNumber;
        this.transitPort = transitPort;
        this.transitDays = transitDays;
        this.estimatedCost = estimatedCost;
        this.rank = rank;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVoyageNumber() { return voyageNumber; }
    public String getTransitPort() { return transitPort; }
    public int getTransitDays() { return transitDays; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public int getRank() { return rank; }
}
