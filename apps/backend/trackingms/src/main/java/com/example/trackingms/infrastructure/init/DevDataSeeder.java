package com.example.trackingms.infrastructure.init;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.commands.RegisterTrackingExceptionCommand;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingExceptionMapper;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 開発環境用のシードデータ投入（local-h2 / local-docker プロファイル）。
 *
 * <p>ダッシュボードの追跡管理／例外対応メニューを空でない状態にするため、
 * ApplicationReadyEvent 後に CommandGateway 経由でサンプル追跡と例外を投入する。</p>
 */
@Component
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private static final String TRACKING_DELIVERED = "TRK-SAMPLE001";
    private static final String TRACKING_IN_TRANSIT = "TRK-SAMPLE002";
    private static final String TRACKING_DELAYED = "TRK-SAMPLE003";
    private static final String EXCEPTION_DELAY = "exc-sample-delay";

    private final CommandGateway commandGateway;
    private final TrackingSummaryMapper trackingSummaryMapper;
    private final TrackingExceptionMapper trackingExceptionMapper;

    public DevDataSeeder(
            CommandGateway commandGateway,
            TrackingSummaryMapper trackingSummaryMapper,
            TrackingExceptionMapper trackingExceptionMapper
    ) {
        this.commandGateway = commandGateway;
        this.trackingSummaryMapper = trackingSummaryMapper;
        this.trackingExceptionMapper = trackingExceptionMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        log.info("DevDataSeeder: seeding trackingms sample trackings (local-h2/local-docker)");
        try {
            seedTracking(TRACKING_DELIVERED, "BK-001");
            seedTracking(TRACKING_IN_TRANSIT, "BK-002");
            seedTracking(TRACKING_DELAYED, "BK-003");

            advanceStatus(TRACKING_IN_TRANSIT, TransportStatus.IN_TRANSIT, "SGSIN", "V0001");

            seedException(
                    TRACKING_DELAYED,
                    EXCEPTION_DELAY,
                    ExceptionType.DELAY,
                    "JPTYO",
                    "悪天候により出発港で 3 日遅延"
            );

            log.info("DevDataSeeder: trackingms seed complete");
        } catch (Exception e) {
            log.warn("DevDataSeeder: trackingms seed failed (continuing)", e);
        }
    }

    private void seedTracking(String trackingNumber, String bookingId) {
        if (trackingSummaryMapper.findByTrackingNumber(trackingNumber) != null) {
            log.debug("DevDataSeeder: tracking {} already exists, skipping", trackingNumber);
            return;
        }
        try {
            commandGateway.send(new InitializeTrackingCommand(trackingNumber, bookingId))
                    .get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("DevDataSeeder: seeded tracking {}", trackingNumber);
        } catch (Exception e) {
            log.warn("DevDataSeeder: failed to seed tracking {}: {}", trackingNumber, e.getMessage());
        }
    }

    private void advanceStatus(
            String trackingNumber, TransportStatus toStatus, String unlocode, String voyageNumber
    ) {
        try {
            commandGateway.send(new UpdateTransportStatusCommand(
                    trackingNumber,
                    toStatus,
                    unlocode,
                    voyageNumber,
                    LocalDateTime.now().minusHours(2),
                    "Sample dev data: " + toStatus
            )).get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("DevDataSeeder: advanced tracking {} -> {}", trackingNumber, toStatus);
        } catch (Exception e) {
            log.warn("DevDataSeeder: failed to advance tracking {}: {}", trackingNumber, e.getMessage());
        }
    }

    private void seedException(
            String trackingNumber, String exceptionId,
            ExceptionType type, String unlocode, String description
    ) {
        if (trackingExceptionMapper.findById(exceptionId) != null) {
            log.debug("DevDataSeeder: exception {} already exists, skipping", exceptionId);
            return;
        }
        try {
            commandGateway.send(new RegisterTrackingExceptionCommand(
                    trackingNumber,
                    exceptionId,
                    type,
                    LocalDateTime.now().minusHours(6),
                    unlocode,
                    description
            )).get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("DevDataSeeder: seeded exception {}", exceptionId);
        } catch (Exception e) {
            log.warn("DevDataSeeder: failed to seed exception {}: {}", exceptionId, e.getMessage());
        }
    }
}
