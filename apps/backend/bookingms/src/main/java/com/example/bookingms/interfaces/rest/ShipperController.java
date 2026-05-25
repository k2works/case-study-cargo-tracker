package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.ShipperCommandService;
import com.example.bookingms.application.ShipperQueryService;
import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.projections.ShipperProjection;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
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
 * 荷主 REST Controller（US02 / US03 / ADR-0008）。
 *
 * <p>エンドポイント一覧:
 * <ul>
 *   <li>{@code POST /api/v1/shippers} 荷主登録</li>
 *   <li>{@code GET /api/v1/shippers/{shipperId}} 荷主詳細取得</li>
 *   <li>{@code GET /api/v1/shippers?page=&size=} 荷主一覧（ページネーション）</li>
 *   <li>{@code GET /api/v1/shippers/search?email=} 重複検出用の検索（メール完全一致、List 返却）</li>
 * </ul>
 * </p>
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
     * 荷主一覧（ページネーション付き、ADR-0008）。
     */
    @GetMapping
    public ResponseEntity<PageResponse<ShipperResponse>> findAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageRequest pageRequest = new PageRequest(page, size);
        List<ShipperResponse> items = queryService.findAll(pageRequest).stream()
                .map(ShipperResponse::from)
                .toList();
        long totalCount = queryService.count();
        return ResponseEntity.ok(PageResponse.of(items, totalCount, pageRequest.page(), pageRequest.size()));
    }

    /**
     * メール完全一致による荷主検索（重複検出専用、UC02 拡張 4a）。
     *
     * <p>登録時の重複チェックを目的とした補助 API のため、件数は通常 0〜1 件であり
     * ページネーションは不要。{@link List} を直接返す。</p>
     */
    @GetMapping("/search")
    public ResponseEntity<List<ShipperResponse>> searchByEmail(@RequestParam("email") String email) {
        List<ShipperResponse> responses = queryService.findByEmail(email).stream()
                .map(ShipperResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
