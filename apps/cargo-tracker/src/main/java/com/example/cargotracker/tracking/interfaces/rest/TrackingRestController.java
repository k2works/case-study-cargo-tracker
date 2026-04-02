package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import com.example.cargotracker.tracking.interfaces.rest.dto.TrackingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "tracking", description = "追跡情報 API — 追跡番号による貨物追跡情報の照会")
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingRestController {

    private final TrackingQueryService trackingQueryService;

    public TrackingRestController(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping("/{trackingNumber}")
    @Operation(summary = "追跡情報取得", description = "追跡番号で貨物の追跡情報と荷役履歴を取得する。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "取得成功"),
        @ApiResponse(responseCode = "404", description = "指定した追跡番号が存在しない")
    })
    public ResponseEntity<TrackingResponse> getByTrackingNumber(@PathVariable String trackingNumber) {
        return trackingQueryService.findTrackingInfo(trackingNumber)
                .map(dto -> ResponseEntity.ok(TrackingResponse.from(dto)))
                .orElse(ResponseEntity.notFound().build());
    }
}
