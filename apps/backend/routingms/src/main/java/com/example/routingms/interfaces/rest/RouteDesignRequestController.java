package com.example.routingms.interfaces.rest;

import com.example.routingms.application.RouteDesignRequestQueryService;
import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.interfaces.rest.dto.RouteDesignRequestResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 経路設計依頼 参照 REST Controller（US06 / cross-service、ADR-0009）。
 *
 * <p>bookingms が発行した {@code RouteDesignRequestedEvent} を Kafka（cargo-events）経由で
 * 受信し routingms に記録した経路設計待ちリスト（route_design_request）を公開する。
 * 経路設計者のワークベンチ（IT4 / US08）の入力であり、cross-service 伝搬の検証にも用いる。</p>
 */
@RestController
@RequestMapping("/api/v1/routes/design-requests")
public class RouteDesignRequestController {

    private final RouteDesignRequestQueryService queryService;

    public RouteDesignRequestController(RouteDesignRequestQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<RouteDesignRequestResponse> findByBookingId(@PathVariable String bookingId) {
        RouteDesignRequestProjection projection = queryService.findByBookingId(bookingId);
        if (projection == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(RouteDesignRequestResponse.from(projection));
    }

    @GetMapping
    public ResponseEntity<List<RouteDesignRequestResponse>> findAll() {
        List<RouteDesignRequestResponse> list = queryService.findAll().stream()
                .map(RouteDesignRequestResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }
}
