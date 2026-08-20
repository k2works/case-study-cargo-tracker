package com.example.routingms.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

/** voyage テーブルの 1 行。永続化の都合をドメインに持ち込まないための入れ物。 */
public class VoyageRecord {

    private Long id;
    private String voyageNumber;
    private String vesselName;
    private String carrierName;
    private String supportedCargoTypes;
    private List<CarrierMovementRecord> movements = new ArrayList<>();

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

    public String getSupportedCargoTypes() {
        return supportedCargoTypes;
    }

    public void setSupportedCargoTypes(String supportedCargoTypes) {
        this.supportedCargoTypes = supportedCargoTypes;
    }

    public List<CarrierMovementRecord> getMovements() {
        return movements;
    }

    public void setMovements(List<CarrierMovementRecord> movements) {
        this.movements = movements;
    }
}
