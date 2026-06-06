package com.example.handlingms.infrastructure.init;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.model.ClaimVerification;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.infrastructure.repositories.mybatis.HandlingActivityMapper;
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
 * <p>ダッシュボードの荷役作業メニューを空でない状態にするため、
 * ApplicationReadyEvent 後に CommandGateway 経由でサンプル荷役活動を投入する。
 * RECEIVE → LOAD → UNLOAD → CUSTOMS → CLAIM の 5 タイプを 1 件ずつ揃える。</p>
 */
@Component
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final String TRACKING_NUMBER = "TRK-SAMPLE002";
    private static final String HANDLER_ID = "handler-dev";

    private final CommandGateway commandGateway;
    private final HandlingActivityMapper handlingActivityMapper;

    public DevDataSeeder(
            CommandGateway commandGateway,
            HandlingActivityMapper handlingActivityMapper
    ) {
        this.commandGateway = commandGateway;
        this.handlingActivityMapper = handlingActivityMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        log.info("DevDataSeeder: seeding handlingms sample activities (local-h2/local-docker)");
        try {
            LocalDateTime base = LocalDateTime.now().minusDays(5).withSecond(0).withNano(0);

            sendActivity(
                    "ACT-SAMPLE001",
                    HandlingType.RECEIVE,
                    base,
                    "JPTYO",
                    null,
                    null
            );
            sendActivity(
                    "ACT-SAMPLE002",
                    HandlingType.LOAD,
                    base.plusHours(6),
                    "JPTYO",
                    "V0001",
                    null
            );
            sendActivity(
                    "ACT-SAMPLE003",
                    HandlingType.UNLOAD,
                    base.plusDays(3),
                    "SGSIN",
                    "V0001",
                    null
            );
            sendActivity(
                    "ACT-SAMPLE004",
                    HandlingType.CUSTOMS,
                    base.plusDays(3).plusHours(4),
                    "SGSIN",
                    null,
                    null
            );

            log.info("DevDataSeeder: handlingms seed complete");
        } catch (Exception e) {
            log.warn("DevDataSeeder: handlingms seed failed (continuing)", e);
        }
    }

    @SuppressWarnings("java:S107")
    private void sendActivity(
            String activityId, HandlingType type, LocalDateTime occurredAt,
            String unlocode, String voyageNumber, ClaimVerification claim
    ) {
        if (handlingActivityMapper.findById(activityId) != null) {
            log.debug("DevDataSeeder: activity {} already exists, skipping", activityId);
            return;
        }
        try {
            commandGateway.send(new RegisterHandlingActivityCommand(
                    activityId,
                    TRACKING_NUMBER,
                    type,
                    occurredAt,
                    unlocode,
                    voyageNumber,
                    HANDLER_ID,
                    claim
            )).get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("DevDataSeeder: seeded handling activity {} ({})", activityId, type);
        } catch (Exception e) {
            log.warn("DevDataSeeder: failed to seed activity {}: {}", activityId, e.getMessage());
        }
    }
}
