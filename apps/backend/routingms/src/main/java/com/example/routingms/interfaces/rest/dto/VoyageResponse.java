package com.example.routingms.interfaces.rest.dto;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 航海レスポンス DTO
 */
public class VoyageResponse {

    private Long id;
    private String voyageNumber;
    private List<CarrierMovementResponse> carrierMovements;

    public VoyageResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public List<CarrierMovementResponse> getCarrierMovements() { return carrierMovements; }
    public void setCarrierMovements(List<CarrierMovementResponse> carrierMovements) {
        this.carrierMovements = carrierMovements;
    }

    /**
     * キャリア移動レスポンスの内部クラス
     */
    public static class CarrierMovementResponse {
        private String departureLocationUnlocode;
        private String arrivalLocationUnlocode;
        private ZonedDateTime departureDate;
        private ZonedDateTime arrivalDate;
        private int seqNumber;

        public CarrierMovementResponse() {}

        public String getDepartureLocationUnlocode() { return departureLocationUnlocode; }
        public void setDepartureLocationUnlocode(String v) { this.departureLocationUnlocode = v; }

        public String getArrivalLocationUnlocode() { return arrivalLocationUnlocode; }
        public void setArrivalLocationUnlocode(String v) { this.arrivalLocationUnlocode = v; }

        public ZonedDateTime getDepartureDate() { return departureDate; }
        public void setDepartureDate(ZonedDateTime v) { this.departureDate = v; }

        public ZonedDateTime getArrivalDate() { return arrivalDate; }
        public void setArrivalDate(ZonedDateTime v) { this.arrivalDate = v; }

        public int getSeqNumber() { return seqNumber; }
        public void setSeqNumber(int v) { this.seqNumber = v; }
    }
}
