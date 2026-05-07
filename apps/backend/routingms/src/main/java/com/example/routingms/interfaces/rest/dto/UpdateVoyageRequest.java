package com.example.routingms.interfaces.rest.dto;

import java.util.List;

/**
 * 航海更新リクエスト DTO
 */
public class UpdateVoyageRequest {

    private List<CarrierMovementRequest> carrierMovements;

    public UpdateVoyageRequest() {}

    public List<CarrierMovementRequest> getCarrierMovements() { return carrierMovements; }
    public void setCarrierMovements(List<CarrierMovementRequest> carrierMovements) {
        this.carrierMovements = carrierMovements;
    }
}
