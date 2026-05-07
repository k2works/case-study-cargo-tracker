package com.example.routingms.interfaces.rest.dto;

import java.util.List;

/**
 * 航海登録リクエスト DTO
 */
public class CreateVoyageRequest {

    private String voyageNumber;
    private List<CarrierMovementRequest> carrierMovements;

    public CreateVoyageRequest() {}

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public List<CarrierMovementRequest> getCarrierMovements() { return carrierMovements; }
    public void setCarrierMovements(List<CarrierMovementRequest> carrierMovements) {
        this.carrierMovements = carrierMovements;
    }
}
