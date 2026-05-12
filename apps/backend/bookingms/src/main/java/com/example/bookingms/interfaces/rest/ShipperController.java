package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.commandservices.ShipperCommandService;
import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.ports.ShipperRepository;
import com.example.bookingms.interfaces.rest.dto.RegisterShipperRequest;
import com.example.bookingms.interfaces.rest.dto.ShipperResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 荷主 REST コントローラー
 */
@RestController
@RequestMapping("/api/booking/v1/shippers")
public class ShipperController {

    private final ShipperCommandService shipperCommandService;
    private final ShipperRepository shipperRepository;

    public ShipperController(ShipperCommandService shipperCommandService,
                              ShipperRepository shipperRepository) {
        this.shipperCommandService = shipperCommandService;
        this.shipperRepository = shipperRepository;
    }

    @PostMapping
    public ResponseEntity<ShipperResponse> register(@RequestBody RegisterShipperRequest request) {
        ShipperCommandService.RegisterShipperCommand command = new ShipperCommandService.RegisterShipperCommand(
                request.name(), request.email(), request.phone(),
                request.shipperType(), request.contractNumber(), request.discountRate());
        Shipper shipper = shipperCommandService.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShipperResponse.from(shipper));
    }

    @GetMapping
    public ResponseEntity<List<ShipperResponse>> findAll() {
        List<ShipperResponse> responses = shipperRepository.findAll().stream()
                .map(ShipperResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
