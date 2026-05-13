package com.example.cargotracker.bookingms.interfaces.rest;

import com.example.cargotracker.bookingms.application.ShipperCommandService;
import com.example.cargotracker.bookingms.application.ShipperCommandService.RegisterShipperCommand;
import com.example.cargotracker.bookingms.domain.model.aggregates.Shipper;
import com.example.cargotracker.bookingms.domain.ports.ShipperRepository;
import com.example.cargotracker.bookingms.interfaces.rest.dto.RegisterShipperRequest;
import com.example.cargotracker.bookingms.interfaces.rest.dto.ShipperResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shippers")
public class ShipperController {

    private final ShipperCommandService commandService;
    private final ShipperRepository repository;

    public ShipperController(ShipperCommandService commandService, ShipperRepository repository) {
        this.commandService = commandService;
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipperResponse register(@Valid @RequestBody RegisterShipperRequest request) {
        RegisterShipperCommand command = new RegisterShipperCommand(
                request.name(), request.email(), request.phone(),
                request.shipperType(), request.contractNumber(), request.discountRate());
        Shipper shipper = commandService.register(command);
        return ShipperResponse.from(shipper);
    }

    @GetMapping
    public List<ShipperResponse> list() {
        return repository.findAll().stream().map(ShipperResponse::from).toList();
    }
}
