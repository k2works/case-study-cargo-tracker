package com.example.cargotracker.estimation.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

public class EstimateRecord {
    public Long id;
    public String estimateId;
    public String originUnlocode;
    public String destinationUnlocode;
    public LocalDate arrivalDeadline;
    public String cargoType;
    public BigDecimal weightKg;
    public String status;
}
