package com.example.cargotracker.routing.infrastructure.repositories;

/** {@code voyage} テーブルの行。ドメインモデルとは分離する。 */
public class VoyageRecord {

    private Long id;
    private String voyageNumber;
    private String vesselName;
    private String carrierName;
    private String cargoTypes;
    private java.math.BigDecimal capacityWeightKg;
    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getVesselName() {
        return vesselName;
    }

    public void setVesselName(String vesselName) {
        this.vesselName = vesselName;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getCargoTypes() {
        return cargoTypes;
    }

    public void setCargoTypes(String cargoTypes) {
        this.cargoTypes = cargoTypes;
    }

    public java.math.BigDecimal getCapacityWeightKg() {
        return capacityWeightKg;
    }

    public void setCapacityWeightKg(java.math.BigDecimal capacityWeightKg) {
        this.capacityWeightKg = capacityWeightKg;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
