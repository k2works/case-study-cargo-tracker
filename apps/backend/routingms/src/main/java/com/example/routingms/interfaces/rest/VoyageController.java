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
        List<CarrierMovementData> movements = request.movements() == null ? List.of() :
                request.movements().stream()
                        .map(m -> new CarrierMovementData(
                                m.departureUnlocode(),
                                m.arrivalUnlocode(),
                                m.departureTime(),
                                m.arrivalTime()))
                        .toList();

        RegisterVoyageCommand command = new RegisterVoyageCommand(
                request.voyageNumber(),
                request.carrierCode(),
                request.carrierName(),
                request.shipName(),
                request.originUnlocode(),
                request.destUnlocode(),
                request.departureDate(),
                request.arrivalDate(),
                movements,
                request.acceptedCargoTypes() == null ? List.of() : request.acceptedCargoTypes()
        );

        commandService.register(command).join();
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{voyageNumber}")
    public ResponseEntity<Void> update(@PathVariable String voyageNumber,
                                       @RequestBody UpdateVoyageScheduleRequest request) {
        List<CarrierMovementData> movements = request.movements() == null ? List.of() :
                request.movements().stream()
                        .map(m -> new CarrierMovementData(
                                m.departureUnlocode(),
                                m.arrivalUnlocode(),
                                m.departureTime(),
                                m.arrivalTime()))
                        .toList();

        UpdateVoyageScheduleCommand command = new UpdateVoyageScheduleCommand(
                voyageNumber,
                request.departureDate(),
                request.arrivalDate(),
                movements,
                request.acceptedCargoTypes() == null ? List.of() : request.acceptedCargoTypes()
        );

        commandService.update(command).join();
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<VoyageResponse>> findAll() {
        List<VoyageResponse> list = queryService.findAll().stream()
                .map(VoyageResponse::from)
                .toList();
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
