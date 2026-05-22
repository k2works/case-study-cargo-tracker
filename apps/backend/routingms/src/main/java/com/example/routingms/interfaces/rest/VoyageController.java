package com.example.routingms.interfaces.rest;

import com.example.routingms.application.VoyageCommandService;
import com.example.routingms.application.VoyageQueryService;
import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;
import com.example.routingms.domain.commands.UpdateVoyageScheduleCommand;
import com.example.routingms.interfaces.rest.dto.RegisterVoyageRequest;
import com.example.routingms.interfaces.rest.dto.UpdateVoyageScheduleRequest;
import com.example.routingms.interfaces.rest.dto.VoyageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/voyages")
public class VoyageController {

    private final VoyageCommandService commandService;
    private final VoyageQueryService queryService;

    public VoyageController(VoyageCommandService commandService, VoyageQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<Void> register(@RequestBody RegisterVoyageRequest request) {
        List<CarrierMovementData> movements = request.getMovements() == null ? List.of() :
                request.getMovements().stream()
                        .map(m -> new CarrierMovementData(
                                m.getDepartureUnlocode(),
                                m.getArrivalUnlocode(),
                                m.getDepartureTime(),
                                m.getArrivalTime()))
                        .collect(Collectors.toList());

        RegisterVoyageCommand command = new RegisterVoyageCommand(
                request.getVoyageNumber(),
                request.getCarrierCode(),
                request.getCarrierName(),
                request.getShipName(),
                request.getOriginUnlocode(),
                request.getDestUnlocode(),
                request.getDepartureDate(),
                request.getArrivalDate(),
                movements,
                request.getAcceptedCargoTypes() == null ? List.of() : request.getAcceptedCargoTypes()
        );

        commandService.register(command).join();
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{voyageNumber}")
    public ResponseEntity<Void> update(@PathVariable String voyageNumber,
                                       @RequestBody UpdateVoyageScheduleRequest request) {
        List<CarrierMovementData> movements = request.getMovements() == null ? List.of() :
                request.getMovements().stream()
                        .map(m -> new CarrierMovementData(
                                m.getDepartureUnlocode(),
                                m.getArrivalUnlocode(),
                                m.getDepartureTime(),
                                m.getArrivalTime()))
                        .collect(Collectors.toList());

        UpdateVoyageScheduleCommand command = new UpdateVoyageScheduleCommand(
                voyageNumber,
                request.getDepartureDate(),
                request.getArrivalDate(),
                movements,
                request.getAcceptedCargoTypes() == null ? List.of() : request.getAcceptedCargoTypes()
        );

        commandService.update(command).join();
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<VoyageResponse>> findAll() {
        List<VoyageResponse> list = queryService.findAll().stream()
                .map(VoyageResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{voyageNumber}")
    public ResponseEntity<VoyageResponse> findByVoyageNumber(@PathVariable String voyageNumber) {
        var projection = queryService.findByVoyageNumber(voyageNumber);
        if (projection == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(VoyageResponse.from(projection));
    }
}
