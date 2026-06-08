package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.TrackingQueryService;
import com.example.trackingms.domain.projections.TrackingEvent;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.interfaces.rest.dto.PublicTrackingResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingEventResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公開追跡照会の REST Controller（US18 / ADR-0013）。
 *
 * <p>荷主・荷受人がメール内 URL（{@code /tracking/{tn}?token=<JWT>}）でアクセスする公開エンドポイント。
 * JWT 検証は {@link PublicTrackingTokenFilter} が servlet チェーン前段で実施するため、本 Controller では
 * 認証済み前提でサマリ・イベント履歴を返却する。</p>
 *
 * <p>ベース URL を {@code /api/v1/public/...} と明確に分離することで、フィルタ適用範囲と
 * 認証必須エンドポイントを URL レベルで判別可能にする。</p>
 *
 * <p>認可（IT10 A1.1 / US30）: 本 Controller は時限署名トークン（JWT）検証で代替認証するため
 * {@code @PreAuthorize} を付与しない。{@link com.example.trackingms.config.HerokuSecurityConfig#publicTrackingFilterChain}
 * 側で {@code /api/v1/public/tracking/**} を {@code permitAll} に設定し、
 * {@link PublicTrackingTokenFilter} が JWT 検証を担う。</p>
 */
@RestController
@RequestMapping("/api/v1/public/tracking")
public class PublicTrackingController {

    private final TrackingQueryService queryService;

    public PublicTrackingController(TrackingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<PublicTrackingResponse> getPublicTracking(
            @PathVariable String trackingNumber) {
        TrackingSummary summary = queryService.findByTrackingNumber(trackingNumber);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        List<TrackingEvent> events = queryService.findEvents(trackingNumber);
        PublicTrackingResponse body = new PublicTrackingResponse(
                TrackingSummaryResponse.from(summary),
                events.stream().map(TrackingEventResponse::from).toList()
        );
        return ResponseEntity.ok(body);
    }
}
