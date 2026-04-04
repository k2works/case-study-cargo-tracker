package com.example.cargotracker.shipper.interfaces.rest;

import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommand;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.interfaces.rest.dto.RegisterShipperRequest;
import com.example.cargotracker.shipper.interfaces.rest.dto.ShipperResponse;
import com.example.cargotracker.shipper.interfaces.rest.transform.ShipperAssembler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shippers")
public class ShipperRestController {

    private final RegisterShipperCommandService registerShipperCommandService;
    private final FindShipperQueryService findShipperQueryService;
    private final ShipperAssembler shipperAssembler;

    public ShipperRestController(
            RegisterShipperCommandService registerShipperCommandService,
            FindShipperQueryService findShipperQueryService,
            ShipperAssembler shipperAssembler
    ) {
        this.registerShipperCommandService = registerShipperCommandService;
        this.findShipperQueryService = findShipperQueryService;
        this.shipperAssembler = shipperAssembler;
    }

    @PostMapping
    public ResponseEntity<IdResponse> register(@Valid @RequestBody RegisterShipperRequest request) {
        ShipperId shipperId = registerShipperCommandService.registerShipper(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(shipperId.toString()));
    }

    @GetMapping("/{id}")
    public ShipperResponse findById(@PathVariable String id) {
        return findShipperQueryService.findById(new ShipperId(UUID.fromString(id)))
                .map(shipperAssembler::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public List<ShipperResponse> findAll() {
        return findShipperQueryService.findAll().stream()
                .map(shipperAssembler::toResponse)
                .toList();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException exception) {
        if (!"EMAIL_ALREADY_REGISTERED".equals(exception.getMessage())) {
            throw exception;
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("EMAIL_ALREADY_REGISTERED", "このメールアドレスは既に登録されています。"));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    private RegisterShipperCommand toCommand(RegisterShipperRequest request) {
        return new RegisterShipperCommand(
                null,
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getShipperType(),
                request.getContractNumber(),
                request.getDiscountRate()
        );
    }

    private record IdResponse(String id) {
    }

    private record ErrorResponse(String code, String message) {
    }
}
