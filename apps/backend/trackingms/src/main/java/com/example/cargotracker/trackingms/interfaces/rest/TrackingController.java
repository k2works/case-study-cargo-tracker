package com.example.cargotracker.trackingms.interfaces.rest;

import com.example.cargotracker.trackingms.application.query.TrackingQueryService;
import com.example.cargotracker.trackingms.domain.model.commands.InitializeTrackingCommand;
import com.example.cargotracker.trackingms.domain.model.services.InvalidTrackingTokenException;
import com.example.cargotracker.trackingms.domain.model.services.TrackingTokenExpiredException;
import com.example.cargotracker.trackingms.domain.model.services.TrackingTokenService;
import com.example.cargotracker.trackingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.trackingms.domain.model.valueobjects.JwtToken;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Leg;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.trackingms.interfaces.rest.dto.InitializeTrackingRequest;
import com.example.cargotracker.trackingms.interfaces.rest.dto.IssueTrackingTokenResponse;
import com.example.cargotracker.trackingms.interfaces.rest.dto.TrackingListItemResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 追跡 REST API（US18 / TI06）。
 *
 * <ul>
 *   <li>{@code GET /api/v1/tracking/{trackingNumber}?token=<JWT>} — 公開追跡照会（gatewayms 経由でログイン不要）</li>
 *   <li>{@code POST /api/v1/tracking/_internal/issue-token} — JWT 発行（gatewayms 経由で管理者認証）</li>
 *   <li>{@code POST /api/v1/tracking/_internal/initialize} — 追跡集約初期化（IT6 暫定。TI06 で Event 駆動化）</li>
 * </ul>
 *
 * <p>関連 ADR: ADR-0013 / iteration_plan-6.md API 設計セクション</p>
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingController.class);
    private static final String ERROR_CODE = "errorCode";
    private static final String MESSAGE_KEY = "message";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private final TrackingTokenService tokenService;
    private final TrackingQueryService queryService;
    private final CommandGateway commandGateway;

    public TrackingController(
            TrackingTokenService tokenService,
            TrackingQueryService queryService,
            CommandGateway commandGateway) {
        this.tokenService = tokenService;
        this.queryService = queryService;
        this.commandGateway = commandGateway;
    }

    /**
     * S16 追跡管理一覧（管理者用）。最終更新日時降順で tracking_summary 全件を返す。
     *
     * <p>gatewayms 側で {@code /api/v1/tracking}（完全一致）は管理者 JWT 認証必須として扱う。
     * {@code /api/v1/tracking/{tn}} の公開照会と URL を共有するため、認証ルール側で切り分ける。</p>
     */
    @GetMapping
    public ResponseEntity<List<TrackingListItemResponse>> listTrackings() {
        return ResponseEntity.ok(queryService.findAll());
    }

    /**
     * US18: 公開追跡照会。JWT 検証 → tracking_summary + tracking_event を返却。
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<?> getTracking(
            @PathVariable String trackingNumber,
            @RequestParam("token") String token) {

        var deliveredAt = queryService.findDeliveredAt(trackingNumber).orElse(null);

        TrackingNumber verified;
        try {
            verified = tokenService.verify(token, deliveredAt);
        } catch (TrackingTokenExpiredException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    ERROR_CODE, "TOKEN_EXPIRED",
                    MESSAGE_KEY, "リンクの有効期限が切れています"));
        } catch (InvalidTrackingTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    ERROR_CODE, "TOKEN_INVALID",
                    MESSAGE_KEY, "リンクが不正です"));
        }

        if (!verified.value().equals(trackingNumber)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    ERROR_CODE, "TOKEN_TN_MISMATCH",
                    MESSAGE_KEY, "リンクが不正です"));
        }

        // validUntil の再計算: クライアントへ表示する有効期限（min(exp, deliveredAt + grace)）
        // ここでは issue 時の値を再現できないため、deliveredAt の有無のみで判定する簡易計算とする
        var dummyValidUntil = LocalDateTime.now().plusDays(30);
        return queryService.findByTrackingNumber(trackingNumber, dummyValidUntil)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        ERROR_CODE, "TRACKING_NOT_FOUND",
                        MESSAGE_KEY, "追跡情報が見つかりません")));
    }

    /**
     * US18: 管理者用 JWT 発行 API（gatewayms で管理者 JWT 認証必須）。
     */
    @PostMapping("/_internal/issue-token")
    public ResponseEntity<IssueTrackingTokenResponse> issueToken(
            @RequestBody Map<String, String> request) {

        String trackingNumberValue = request.get("trackingNumber");
        if (trackingNumberValue == null || trackingNumberValue.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        var trackingNumber = new TrackingNumber(trackingNumberValue);
        var deliveredAt = queryService.findDeliveredAt(trackingNumberValue).orElse(null);
        JwtToken jwt = tokenService.issue(trackingNumber, deliveredAt);

        var url = "/tracking/" + trackingNumberValue + "?token=" + jwt.token();
        return ResponseEntity.ok(new IssueTrackingTokenResponse(url, jwt.token(), jwt.validUntil()));
    }

    /**
     * US18 暫定: 内部初期化 API。TI06 で {@code CargoTrackedEvent} 駆動化されると廃止される。
     */
    @PostMapping("/_internal/initialize")
    public ResponseEntity<Map<String, String>> initialize(
            @RequestBody InitializeTrackingRequest request) {

        var itinerary = new CargoItinerary(List.of(new Leg(
                request.voyageNumber(),
                Location.of(request.originUnlocode()),
                Location.of(request.destinationUnlocode()),
                LocalDateTime.now(),
                request.estimatedArrival())));

        var command = new InitializeTrackingCommand(
                request.trackingNumber(),
                request.bookingId(),
                itinerary,
                Location.of(request.originUnlocode()),
                Location.of(request.destinationUnlocode()),
                request.estimatedArrival());

        sendAndWaitWithTimeout(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "trackingNumber", request.trackingNumber()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        LOG.debug("Bad request", e);
        return ResponseEntity.badRequest().body(Map.of(
                ERROR_CODE, "VALIDATION_ERROR",
                MESSAGE_KEY, e.getMessage()));
    }

    private void sendAndWaitWithTimeout(Object command) {
        try {
            commandGateway.send(command, Object.class)
                    .orTimeout(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof TimeoutException) {
                throw new IllegalStateException(
                        "コマンド処理がタイムアウトしました（" + COMMAND_TIMEOUT.toSeconds() + "秒）", cause);
            }
            throw new IllegalStateException("コマンド実行に失敗しました", cause);
        }
    }
}
