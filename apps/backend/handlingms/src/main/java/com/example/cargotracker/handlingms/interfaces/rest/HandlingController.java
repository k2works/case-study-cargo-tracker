package com.example.cargotracker.handlingms.interfaces.rest;

import com.example.cargotracker.handlingms.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handlingms.domain.model.commands.UpdateCargoStatusCommand;
import com.example.cargotracker.handlingms.domain.model.valueobjects.ClaimVerification;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.handlingms.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.handlingms.domain.ports.CargoSnapshotRepository;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotRecord;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoStatusHistoryMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoStatusHistoryRecord;
import com.example.cargotracker.handlingms.infrastructure.persistence.HandlingActivityMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.HandlingActivityRecord;
import com.example.cargotracker.handlingms.interfaces.rest.dto.CargoStatusUpdateResponse;
import com.example.cargotracker.handlingms.interfaces.rest.dto.HandlingActivityResponse;
import com.example.cargotracker.handlingms.interfaces.rest.dto.RegisterCargoSnapshotRequest;
import com.example.cargotracker.handlingms.interfaces.rest.dto.RegisterHandlingActivityRequest;
import com.example.cargotracker.handlingms.interfaces.rest.dto.UpdateCargoStatusRequest;
import jakarta.validation.Valid;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 荷役 REST API（US15 / US16）。
 *
 * <p>POST /api/v1/handling/activities: 荷役作業を登録する。</p>
 * <p>GET  /api/v1/handling/activities/{trackingNumber}: 追跡番号別の作業履歴照会。</p>
 * <p>POST /api/v1/handling/cargo-snapshots: CargoSnapshot 登録（IT5 暫定 ACL、IT6 で Event 購読化）。</p>
 *
 * <p>関連: ADR-0012 / iteration_plan-5.md</p>
 */
@RestController
@RequestMapping("/api/v1/handling")
public class HandlingController {

    private static final String MESSAGE_KEY = "message";
    private static final String TRACKING_NUMBER_NOT_FOUND_PREFIX = "追跡番号が存在しません: ";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private final CommandGateway commandGateway;
    private final CargoSnapshotRepository cargoSnapshotRepository;
    private final CargoSnapshotMapper cargoSnapshotMapper;
    private final HandlingActivityMapper handlingActivityMapper;
    private final CargoStatusHistoryMapper cargoStatusHistoryMapper;

    public HandlingController(
            CommandGateway commandGateway,
            CargoSnapshotRepository cargoSnapshotRepository,
            CargoSnapshotMapper cargoSnapshotMapper,
            HandlingActivityMapper handlingActivityMapper,
            CargoStatusHistoryMapper cargoStatusHistoryMapper) {
        this.commandGateway = commandGateway;
        this.cargoSnapshotRepository = cargoSnapshotRepository;
        this.cargoSnapshotMapper = cargoSnapshotMapper;
        this.handlingActivityMapper = handlingActivityMapper;
        this.cargoStatusHistoryMapper = cargoStatusHistoryMapper;
    }

    /**
     * 荷役作業を登録する（US15 / US16）。
     */
    @PostMapping("/activities")
    public ResponseEntity<Object> registerActivity(@Valid @RequestBody RegisterHandlingActivityRequest request) {
        // 1. CargoSnapshot 引当（受入条件 6: 追跡番号が存在しない場合エラー）
        final TrackingNumber trackingNumber;
        try {
            trackingNumber = new TrackingNumber(request.trackingNumber());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
        var snapshotOpt = cargoSnapshotRepository.findByTrackingNumber(trackingNumber);
        if (snapshotOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY,
                            TRACKING_NUMBER_NOT_FOUND_PREFIX + request.trackingNumber()));
        }

        // 2. コマンド構築（バリデーション付き）
        final RegisterHandlingActivityCommand command;
        final HandlingType type;
        try {
            type = HandlingType.valueOf(request.handlingType());
            command = new RegisterHandlingActivityCommand(
                    UUID.randomUUID().toString(),
                    trackingNumber,
                    type,
                    Location.of(request.unlocode()),
                    request.occurredAt(),
                    request.voyageNumber() == null ? null : new VoyageNumber(request.voyageNumber()),
                    new HandlerId(request.operatorId()),
                    toClaimVerification(request),
                    snapshotOpt.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }

        // 3. CommandGateway 経由で Aggregate に送信（IT4 H2 に倣いタイムアウト 30s）
        sendAndWaitWithTimeout(command);

        // 4. レスポンス（unexpected は CargoSnapshot 判定の結果）
        boolean isUnexpected = !snapshotOpt.get().isExpectedHandling(type, Location.of(request.unlocode()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new HandlingActivityResponse(
                        command.activityId(),
                        request.trackingNumber(),
                        type.name(),
                        request.unlocode(),
                        isUnexpected));
    }

    /**
     * 追跡番号別の作業履歴を照会する（US15 受入 + S21 履歴画面）。
     */
    @GetMapping("/activities/{trackingNumber}")
    public ResponseEntity<List<HandlingActivityRecord>> findByTrackingNumber(
            @PathVariable("trackingNumber") String trackingNumber) {
        var records = handlingActivityMapper.findByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(records);
    }

    /**
     * 追跡番号別の貨物状態手動更新履歴を照会する（US17 受入3 + S17 履歴画面）。
     */
    @GetMapping("/activities/{trackingNumber}/status-history")
    public ResponseEntity<List<CargoStatusHistoryRecord>> findStatusHistoryByTrackingNumber(
            @PathVariable("trackingNumber") String trackingNumber) {
        var records = cargoStatusHistoryMapper.findByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(records);
    }

    /**
     * 追跡番号で現在の貨物概要を照会する（US17 受入1: 現在の貨物情報を確認）。
     */
    @GetMapping("/activities/{trackingNumber}/snapshot")
    public ResponseEntity<Object> findCargoSnapshot(
            @PathVariable("trackingNumber") String trackingNumber) {
        var snapshotOpt = cargoSnapshotRepository.findByTrackingNumber(new TrackingNumber(trackingNumber));
        if (snapshotOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, TRACKING_NUMBER_NOT_FOUND_PREFIX + trackingNumber));
        }
        var snapshot = snapshotOpt.get();
        return ResponseEntity.ok(Map.of(
                "bookingId", snapshot.bookingId(),
                "trackingNumber", trackingNumber,
                "originUnlocode", snapshot.origin().unLocode().value(),
                "destinationUnlocode", snapshot.destination().unLocode().value(),
                "cargoType", snapshot.cargoType()));
    }

    /**
     * 貨物状態を手動更新する（US17・IT5 暫定実装）。
     *
     * <p><b>Deprecated（TI06 以降）</b>: IT6 で trackingms に移管されたため、本エンドポイントは
     * 後方互換のためのみ維持される。新規呼び出しは {@code PUT /api/v1/tracking/{tn}/status} を
     * 使用すること。レスポンスヘッダーで {@code Deprecation: true}, {@code Sunset}（次イテレーション末）,
     * {@code Link: /api/v1/tracking/{tn}/status} を返す（RFC 9745 / RFC 8594）。</p>
     */
    @PutMapping("/activities/{trackingNumber}/status")
    public ResponseEntity<Object> updateStatus(
            @PathVariable("trackingNumber") String trackingNumber,
            @Valid @RequestBody UpdateCargoStatusRequest request) {
        // 1. CargoSnapshot 引当（受入条件: 追跡番号が存在しない場合エラー）
        final TrackingNumber trk;
        try {
            trk = new TrackingNumber(trackingNumber);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
        if (cargoSnapshotRepository.findByTrackingNumber(trk).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, TRACKING_NUMBER_NOT_FOUND_PREFIX + trackingNumber));
        }

        // 2. コマンド構築
        final UpdateCargoStatusCommand command;
        try {
            command = new UpdateCargoStatusCommand(
                    UUID.randomUUID().toString(),
                    trk,
                    request.newStatus(),
                    Location.of(request.unlocode()),
                    request.updatedAt(),
                    new HandlerId(request.operatorId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }

        // 3. CommandGateway 経由で Aggregate に送信
        sendAndWaitWithTimeout(command);

        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", "Sun, 30 Aug 2026 23:59:59 GMT")
                .header("Link",
                        "</api/v1/tracking/" + trackingNumber + "/status>; rel=\"successor-version\"")
                .header("Warning",
                        "299 - \"This endpoint is deprecated. Use PUT /api/v1/tracking/"
                                + trackingNumber + "/status (TI06 移管)\"")
                .body(new CargoStatusUpdateResponse(
                        command.activityId(),
                        trackingNumber,
                        request.newStatus(),
                        request.unlocode()));
    }

    /**
     * CargoSnapshot 登録（IT5 暫定 ACL）。
     *
     * <p>IT6 で Axon Event Bus 経由の CargoBookedEvent 購読に置き換える予定。</p>
     */
    @PostMapping("/cargo-snapshots")
    public ResponseEntity<Object> registerCargoSnapshot(@Valid @RequestBody RegisterCargoSnapshotRequest request) {
        var snapshot = new CargoSnapshotRecord();
        snapshot.setBookingId(request.bookingId());
        snapshot.setTrackingNumber(request.trackingNumber());
        snapshot.setOriginUnlocode(request.originUnlocode());
        snapshot.setDestinationUnlocode(request.destinationUnlocode());
        snapshot.setCargoType(request.cargoType());
        snapshot.setArrivalDeadline(request.arrivalDeadline());
        snapshot.setBookingStatus(request.bookingStatus());
        cargoSnapshotMapper.upsert(snapshot);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "bookingId", request.bookingId(),
                "trackingNumber", request.trackingNumber() == null ? "" : request.trackingNumber()));
    }

    private static ClaimVerification toClaimVerification(RegisterHandlingActivityRequest request) {
        if (request.claimVerification() == null) {
            return null;
        }
        var dto = request.claimVerification();
        return new ClaimVerification(
                dto.consigneeName(),
                dto.signatureRef(),
                dto.confirmationCode(),
                LocalDateTime.now());
    }

    /**
     * IT4 H2 に倣ったタイムアウト付きコマンド送信。Axon 5.1 の send(command, Class) を
     * {@code orTimeout} で囲んで join する。
     */
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
