package com.example.cargotracker.routingms.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * routingms の疎通確認用エンドポイント。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Ping", description = "疎通確認 API")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "疎通確認")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "routingms",
                "phase", "1",
                "timestamp", LocalDateTime.now().toString());
    }
}
