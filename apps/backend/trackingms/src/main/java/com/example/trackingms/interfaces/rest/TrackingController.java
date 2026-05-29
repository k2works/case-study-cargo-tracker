package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.TrackingCommandService;
import com.example.trackingms.application.TrackingQueryService;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.interfaces.rest.dto.TrackingEventResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingSummaryResponse;
import com.example.trackingms.interfaces.rest.dto.UpdateTransportStatusRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 追跡情報の REST Controller（US17 / IT5 2.3）。
 *
 * <p>追跡管理者が貨物の現在状態を確認し、必要に応じて状態を手動更新するためのエンドポイント。
 * 公開照会（US18：時限署名トークン）は IT6 で別 Controller として実装する。</p>
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingCommandService commandService;
    private final TrackingQueryService queryService;

    public TrackingController(TrackingCommandService commandService,
                              TrackingQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<TrackingSummaryResponse> findByTrackingNumber(
            @PathVariable String trackingNumber) {
        TrackingSummary summary = queryService.findByTrackingNumber(trackingNumber);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(TrackingSummaryResponse.from(summary));
    }

    @GetMapping("/{trackingNumber}/events")
    public ResponseEntity<List<TrackingEventResponse>> findEvents(
            @PathVariable String trackingNumber) {
        if (queryService.findByTrackingNumber(trackingNumber) == null) {
            return ResponseEntity.notFound().build();
        }
        List<TrackingEventResponse> events = queryService.findEvents(trackingNumber).stream()
                .map(TrackingEventResponse::from)
                .toList();
        return ResponseEntity.ok(events);
    }

    @PostMapping("/{trackingNumber}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String trackingNumber,
                                             @RequestBody UpdateTransportStatusRequest request) {
        TransportStatus toStatus;
        try {
            toStatus = TransportStatus.valueOf(request.toStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        }
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                trackingNumber,
                toStatus,
                request.unlocode(),
                request.voyageNumber(),
                request.occurredAt(),
                request.description()
        );
        commandService.updateStatus(command).join();
        return ResponseEntity.accepted().build();
    }
}
