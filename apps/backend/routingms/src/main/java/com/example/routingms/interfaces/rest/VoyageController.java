package com.example.routingms.interfaces.rest;

import com.example.routingms.application.service.VoyageCommandService;
import com.example.routingms.application.service.VoyageQueryService;
import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.routingms.interfaces.rest.assembler.VoyageAssembler;
import com.example.routingms.interfaces.rest.dto.CreateVoyageRequest;
import com.example.routingms.interfaces.rest.dto.UpdateVoyageRequest;
import com.example.routingms.interfaces.rest.dto.VoyageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 航海スケジュール REST API コントローラー
 *
 * <pre>
 * POST   /api/v1/voyages               — 航海登録
 * GET    /api/v1/voyages               — 航海一覧
 * GET    /api/v1/voyages/{voyageNumber} — 航海詳細
 * PUT    /api/v1/voyages/{voyageNumber} — 航海更新
 * DELETE /api/v1/voyages/{voyageNumber} — 航海削除
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/voyages")
public class VoyageController {

    private final VoyageCommandService commandService;
    private final VoyageQueryService queryService;
    private final VoyageAssembler assembler;

    public VoyageController(
            VoyageCommandService commandService,
            VoyageQueryService queryService,
            VoyageAssembler assembler) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.assembler = assembler;
    }

    /**
     * 航海登録
     */
    @PostMapping
    public ResponseEntity<VoyageResponse> create(@RequestBody CreateVoyageRequest request) {
        Voyage voyage = assembler.toNewVoyage(request);
        Voyage saved = commandService.register(voyage);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toResponse(saved));
    }

    /**
     * 航海一覧
     */
    @GetMapping
    public ResponseEntity<List<VoyageResponse>> list() {
        List<VoyageResponse> responses = queryService.findAll().stream()
                .map(assembler::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * 航海詳細
     */
    @GetMapping("/{voyageNumber}")
    public ResponseEntity<VoyageResponse> get(@PathVariable String voyageNumber) {
        return queryService.findByVoyageNumber(new VoyageNumber(voyageNumber))
                .map(v -> ResponseEntity.ok(assembler.toResponse(v)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 航海更新（スケジュール差し替え）
     */
    @PutMapping("/{voyageNumber}")
    public ResponseEntity<VoyageResponse> update(
            @PathVariable String voyageNumber,
            @RequestBody UpdateVoyageRequest request) {
        Schedule newSchedule = assembler.toSchedule(request.getCarrierMovements());
        Voyage updated = commandService.updateSchedule(new VoyageNumber(voyageNumber), newSchedule);
        return ResponseEntity.ok(assembler.toResponse(updated));
    }

    /**
     * 航海削除
     */
    @DeleteMapping("/{voyageNumber}")
    public ResponseEntity<Void> delete(@PathVariable String voyageNumber) {
        commandService.delete(new VoyageNumber(voyageNumber));
        return ResponseEntity.noContent().build();
    }

    // --- 例外ハンドリング ---

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
