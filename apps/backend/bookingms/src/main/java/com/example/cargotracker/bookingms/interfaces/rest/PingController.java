package com.example.cargotracker.bookingms.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Walking Skeleton の動作確認用エンドポイント。
 *
 * <p>Phase 0 では Cargo Aggregate などのドメイン実装が無いため、Swagger UI に
 * 表示できる API を 1 本確保する目的でだけ存在する。Phase 1 で実 API が
 * 揃った時点で削除する。
 */
@RestController
@RequestMapping("/api/ping")
@Tag(name = "ping", description = "Walking Skeleton 用の疎通確認エンドポイント")
public class PingController {

    @GetMapping
    @Operation(summary = "サービス疎通確認", description = "サービス名と現在時刻を返す")
    public Map<String, Object> ping() {
        return Map.of(
            "service", "bookingms",
            "phase", "0",
            "timestamp", OffsetDateTime.now().toString()
        );
    }
}
