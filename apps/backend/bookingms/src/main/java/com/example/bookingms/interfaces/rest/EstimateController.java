package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.commandservices.EstimateCommandService;
import com.example.bookingms.domain.model.aggregates.Estimate;
import com.example.bookingms.domain.ports.EstimateRepository;
import com.example.bookingms.interfaces.rest.dto.CreateEstimateRequest;
import com.example.bookingms.interfaces.rest.dto.EstimateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 輸送見積 REST コントローラー
 */
@RestController
@RequestMapping("/api/booking/v1/estimates")
public class EstimateController {

    private final EstimateCommandService estimateCommandService;
    private final EstimateRepository estimateRepository;

    public EstimateController(EstimateCommandService estimateCommandService,
                               EstimateRepository estimateRepository) {
        this.estimateCommandService = estimateCommandService;
        this.estimateRepository = estimateRepository;
    }

    @PostMapping
    public ResponseEntity<EstimateResponse> create(@RequestBody CreateEstimateRequest request) {
        EstimateCommandService.CreateEstimateCommand command = new EstimateCommandService.CreateEstimateCommand(
                request.originUnlocode(), request.destinationUnlocode(),
                request.arrivalDeadline(), request.cargoType(), request.weightKg());
        Estimate estimate = estimateCommandService.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(EstimateResponse.from(estimate));
    }

    @GetMapping("/{estimateId}")
    public ResponseEntity<EstimateResponse> findById(@PathVariable UUID estimateId) {
        return estimateRepository.findByEstimateId(estimateId)
                .map(estimate -> ResponseEntity.ok(EstimateResponse.from(estimate)))
                .orElse(ResponseEntity.notFound().build());
    }
}
