package com.example.cargotracker.shipper.interfaces.rest;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices.DuplicateShipperException;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.application.internal.queryservices.ShipperQueryNotFoundException;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.interfaces.rest.dto.ShipperRequest;
import com.example.cargotracker.shipper.interfaces.rest.dto.ShipperResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shippers")
public class ShipperRestController {

    private final RegisterShipperCommandService registerShipperCommandService;
    private final FindShipperQueryService findShipperQueryService;

    public ShipperRestController(RegisterShipperCommandService registerShipperCommandService,
                                 FindShipperQueryService findShipperQueryService) {
        this.registerShipperCommandService = registerShipperCommandService;
        this.findShipperQueryService = findShipperQueryService;
    }

    @GetMapping
    public List<ShipperResponse> list() {
        return findShipperQueryService.findAll().stream()
                .map(ShipperResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ShipperResponse detail(@PathVariable("id") String id) {
        return ShipperResponse.from(findShipper(id));
    }

    @PostMapping
    public ResponseEntity<ShipperResponse> register(@Valid @RequestBody ShipperRequest request,
                                                    UriComponentsBuilder uriComponentsBuilder) {
        var shipperId = registerShipperCommandService.execute(
                new com.example.cargotracker.shipper.domain.model.commands.RegisterShipperCommand(
                        request.name(),
                        request.email(),
                        request.phone(),
                        CustomerCategory.valueOf(request.category()),
                        request.contractNumber(),
                        request.discountRate()
                )
        );
        URI location = uriComponentsBuilder.path("/api/shippers/{id}")
                .buildAndExpand(shipperId)
                .toUri();
        return ResponseEntity.created(location).body(ShipperResponse.from(findShipper(shipperId.toString())));
    }

    @ExceptionHandler(ShipperQueryNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ShipperQueryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problemDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler({DuplicateShipperException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(problemDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    private com.example.cargotracker.shipper.domain.model.aggregates.Shipper findShipper(String id) {
        try {
            return findShipperQueryService.execute(new ShipperId(UUID.fromString(id)));
        } catch (IllegalArgumentException _) {
            throw new ShipperQueryNotFoundException(id);
        }
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return problemDetail;
    }
}
