package com.example.cargotracker.estimation.infrastructure.repositories;

import java.math.BigDecimal;

public class RouteCandidateRecord {
    public Long id;
    public Long estimateId;
    public String voyageNumber;
    public String transitPort;
    public int transitDays;
    public BigDecimal estimatedCost;
    public int rank;
}
