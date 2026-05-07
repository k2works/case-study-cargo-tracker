package com.example.routingms.interfaces.rest.assembler;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.entities.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.routingms.interfaces.rest.dto.CarrierMovementRequest;
import com.example.routingms.interfaces.rest.dto.CreateVoyageRequest;
import com.example.routingms.interfaces.rest.dto.UpdateVoyageRequest;
import com.example.routingms.interfaces.rest.dto.VoyageResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Voyage ドメインオブジェクトと DTO の相互変換を担うアセンブラ
 */
@Component
public class VoyageAssembler {

    /**
     * CreateVoyageRequest → Voyage（新規）
     */
    public Voyage toNewVoyage(CreateVoyageRequest request) {
        VoyageNumber voyageNumber = new VoyageNumber(request.getVoyageNumber());
        Schedule schedule = toSchedule(request.getCarrierMovements());
        return new Voyage(voyageNumber, schedule);
    }

    /**
     * UpdateVoyageRequest のキャリア移動リスト → Schedule
     */
    public Schedule toSchedule(List<CarrierMovementRequest> requests) {
        List<CarrierMovement> movements = requests.stream()
                .map(r -> new CarrierMovement(
                        r.getDepartureLocationUnlocode(),
                        r.getArrivalLocationUnlocode(),
                        r.getDepartureDate(),
                        r.getArrivalDate(),
                        r.getSeqNumber()))
                .toList();
        return new Schedule(movements);
    }

    /**
     * Voyage → VoyageResponse
     */
    public VoyageResponse toResponse(Voyage voyage) {
        VoyageResponse response = new VoyageResponse();
        response.setId(voyage.getId());
        response.setVoyageNumber(voyage.getVoyageNumber().getNumber());

        List<VoyageResponse.CarrierMovementResponse> cmResponses =
                voyage.getSchedule().getCarrierMovements().stream()
                        .map(this::toCarrierMovementResponse)
                        .toList();
        response.setCarrierMovements(cmResponses);
        return response;
    }

    private VoyageResponse.CarrierMovementResponse toCarrierMovementResponse(CarrierMovement cm) {
        VoyageResponse.CarrierMovementResponse r = new VoyageResponse.CarrierMovementResponse();
        r.setDepartureLocationUnlocode(cm.getDepartureLocationUnlocode());
        r.setArrivalLocationUnlocode(cm.getArrivalLocationUnlocode());
        r.setDepartureDate(cm.getDepartureDate());
        r.setArrivalDate(cm.getArrivalDate());
        r.setSeqNumber(cm.getSeqNumber());
        return r;
    }
}
