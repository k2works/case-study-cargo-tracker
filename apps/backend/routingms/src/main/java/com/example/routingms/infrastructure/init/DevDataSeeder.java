package com.example.routingms.infrastructure.init;

import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;
import com.example.routingms.infrastructure.repositories.mybatis.VoyageMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 開発環境用のシードデータ投入（local-h2 / local-docker プロファイル）。
 *
 * <p>ダッシュボードの航海スケジュール／経路設計メニューを空でない状態にするため、
 * ApplicationReadyEvent 後に CommandGateway 経由でサンプル航海を投入する。
 * BFS 多段経路探索のテストデータとして TYO → SIN → LAX → NYK の 3 航海を用意。</p>
 */
@Component
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private static final String UNLOCODE_TYO = "JPTYO";
    private static final String UNLOCODE_SIN = "SGSIN";
    private static final String UNLOCODE_LAX = "USLAX";
    private static final String UNLOCODE_NYK = "USNYK";

    private final CommandGateway commandGateway;
    private final VoyageMapper voyageMapper;

    public DevDataSeeder(CommandGateway commandGateway, VoyageMapper voyageMapper) {
        this.commandGateway = commandGateway;
        this.voyageMapper = voyageMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        log.info("DevDataSeeder: seeding routingms sample voyages (local-h2/local-docker)");
        try {
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

            sendVoyage(
                    "V0001", "MAERSK", "Maersk Line", "Maersk Tokyo",
                    UNLOCODE_TYO, UNLOCODE_SIN,
                    now.plusDays(3), now.plusDays(10),
                    List.of(new CarrierMovementData(
                            UNLOCODE_TYO, UNLOCODE_SIN,
                            now.plusDays(3), now.plusDays(10)
                    ))
            );
            sendVoyage(
                    "V0002", "MSC", "Mediterranean Shipping", "MSC Singapore",
                    UNLOCODE_SIN, UNLOCODE_LAX,
                    now.plusDays(12), now.plusDays(25),
                    List.of(new CarrierMovementData(
                            UNLOCODE_SIN, UNLOCODE_LAX,
                            now.plusDays(12), now.plusDays(25)
                    ))
            );
            sendVoyage(
                    "V0003", "ONE", "Ocean Network Express", "ONE California",
                    UNLOCODE_LAX, UNLOCODE_NYK,
                    now.plusDays(28), now.plusDays(35),
                    List.of(new CarrierMovementData(
                            UNLOCODE_LAX, UNLOCODE_NYK,
                            now.plusDays(28), now.plusDays(35)
                    ))
            );

            log.info("DevDataSeeder: routingms seed complete");
        } catch (Exception e) {
            log.warn("DevDataSeeder: routingms seed failed (continuing)", e);
        }
    }

    @SuppressWarnings("java:S107")
    private void sendVoyage(
            String voyageNumber, String carrierCode, String carrierName, String shipName,
            String originUnlocode, String destUnlocode,
            LocalDateTime departureDate, LocalDateTime arrivalDate,
            List<CarrierMovementData> movements
    ) {
        if (voyageMapper.findByVoyageNumber(voyageNumber) != null) {
            log.debug("DevDataSeeder: voyage {} already exists, skipping", voyageNumber);
            return;
        }
        try {
            commandGateway.send(new RegisterVoyageCommand(
                    voyageNumber, carrierCode, carrierName, shipName,
                    originUnlocode, destUnlocode,
                    departureDate, arrivalDate,
                    movements,
                    List.of("GENERAL", "HAZARDOUS", "REFRIGERATED")
            )).get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("DevDataSeeder: seeded voyage {}", voyageNumber);
        } catch (Exception e) {
            log.warn("DevDataSeeder: failed to seed voyage {}: {}", voyageNumber, e.getMessage());
        }
    }
}
