package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.ShipperCommandService;
import com.example.bookingms.application.ShipperQueryService;
import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.projections.ShipperProjection;
import com.example.bookingms.interfaces.rest.dto.RegisterShipperRequest;
import com.example.bookingms.interfaces.rest.dto.ShipperResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 荷主 REST Controller（US02 / US03）。
 *
 * <p>POST /api/v1/shippers で登録、GET /api/v1/shippers{?email=} で重複検出可能。
 * 法人荷主の場合は contractNumber / discountRate を含む。</p>
 */
@RestController
@RequestMapping("/api/v1/shippers")
public class ShipperController {

    private final ShipperCommandService commandService;
    private final ShipperQueryService queryService;

    public ShipperController(ShipperCommandService commandService, ShipperQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterShipperRequest request) {
        String shipperId = request.shipperId() == null || request.shipperId().isBlank()
                ? UUID.randomUUID().toString()
                : request.shipperId();

        RegisterShipperCommand command = new RegisterShipperCommand(
                shipperId,
                request.shipperType(),
                request.name(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.countryCode(),
                request.postalCode(),
                request.email(),
                request.phone(),
                request.contractNumber(),
                request.discountRate()
        );

        commandService.register(command).join();
        return ResponseEntity.status(201).body(Map.of("shipperId", shipperId));
    }

    @GetMapping("/{shipperId}")
    public ResponseEntity<ShipperResponse> findByShipperId(@PathVariable String shipperId) {
        ShipperProjection projection = queryService.findByShipperId(shipperId);
        if (projection == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ShipperResponse.from(projection));
    }

    @GetMapping
    public ResponseEntity<List<ShipperResponse>> find(@RequestParam(value = "email", required = false) String email) {
        List<ShipperProjection> projections = email == null || email.isBlank()
                ? queryService.findAll()
                : queryService.findByEmail(email);
        List<ShipperResponse> responses = projections.stream()
                .map(ShipperResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
