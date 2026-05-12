package com.example.bookingms.domain.model.aggregates;

import com.example.bookingms.domain.model.entities.RouteCandidate;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.EstimateStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 輸送見積（Estimate）集約ルート
 */
public class Estimate {

    private Long id;
    private final UUID estimateId;
    private final String originUnlocode;
    private final String destinationUnlocode;
    private final LocalDate arrivalDeadline;
    private final CargoType cargoType;
    private final BigDecimal weightKg;
    private EstimateStatus status;
    private final List<RouteCandidate> candidates;

    /**
     * 新規見積作成ファクトリメソッド
     */
    public static Estimate create(String originUnlocode, String destinationUnlocode,
                                   LocalDate arrivalDeadline, String cargoType, BigDecimal weightKg) {
        Objects.requireNonNull(originUnlocode, "originUnlocode must not be null");
        Objects.requireNonNull(destinationUnlocode, "destinationUnlocode must not be null");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline must not be null");
        Objects.requireNonNull(cargoType, "cargoType must not be null");
        Objects.requireNonNull(weightKg, "weightKg must not be null");
        return new Estimate(null, UUID.randomUUID(), originUnlocode, destinationUnlocode,
                arrivalDeadline, CargoType.valueOf(cargoType), weightKg,
                EstimateStatus.CREATED, new ArrayList<>());
    }

    /**
     * DBからの復元コンストラクタ
     */
    public Estimate(Long id, UUID estimateId, String originUnlocode, String destinationUnlocode,
                    LocalDate arrivalDeadline, CargoType cargoType, BigDecimal weightKg,
                    EstimateStatus status, List<RouteCandidate> candidates) {
        this.id = id;
        this.estimateId = estimateId;
        this.originUnlocode = originUnlocode;
        this.destinationUnlocode = destinationUnlocode;
        this.arrivalDeadline = arrivalDeadline;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        this.status = status;
        this.candidates = candidates != null ? candidates : new ArrayList<>();
    }

    /**
     * ルート候補を差し替える
     */
    public void replaceCandidates(List<RouteCandidate> newCandidates) {
        this.candidates.clear();
        if (newCandidates == null || newCandidates.isEmpty()) {
            this.status = EstimateStatus.NO_ROUTES;
        } else {
            this.candidates.addAll(newCandidates);
            this.status = EstimateStatus.CANDIDATES_AVAILABLE;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getEstimateId() { return estimateId; }
    public String getOriginUnlocode() { return originUnlocode; }
    public String getDestinationUnlocode() { return destinationUnlocode; }
    public LocalDate getArrivalDeadline() { return arrivalDeadline; }
    public CargoType getCargoType() { return cargoType; }
    public BigDecimal getWeightKg() { return weightKg; }
    public EstimateStatus getStatus() { return status; }
    public List<RouteCandidate> getCandidates() { return List.copyOf(candidates); }
}
