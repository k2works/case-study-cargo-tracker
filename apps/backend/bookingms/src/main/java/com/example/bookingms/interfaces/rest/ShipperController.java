package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.ShipperCommandService;
import com.example.bookingms.application.ShipperQueryService;
import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.projections.ShipperProjection;
import com.example.bookingms.interfaces.rest.dto.PageResponse;
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

    /**
     * 荷主一覧。
     *
     * <p>{@code email} 指定時は重複検出用に list を返す。指定なしの場合は
     * ページネーション付きの {@link PageResponse} を返す。
     * フロントエンドは email クエリ有無で戻り型を切り替える。</p>
     */
    @GetMapping
    public ResponseEntity<?> find(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        if (email != null && !email.isBlank()) {
            List<ShipperResponse> responses = queryService.findByEmail(email).stream()
                    .map(ShipperResponse::from)
                    .toList();
            return ResponseEntity.ok(responses);
        }
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        List<ShipperResponse> items = queryService.findAll(safePage, safeSize).stream()
                .map(ShipperResponse::from)
                .toList();
        long totalCount = queryService.count();
        return ResponseEntity.ok(PageResponse.of(items, totalCount, safePage, safeSize));
    }
}
