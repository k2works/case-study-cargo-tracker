package com.example.bookingms.infrastructure.init;

import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.commands.CreateQuotationCommand;
import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.RouteCandidate;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.ShipperType;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import com.example.bookingms.infrastructure.repositories.mybatis.QuotationMapper;
import com.example.bookingms.infrastructure.repositories.mybatis.ShipperMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 開発環境用のシードデータ投入（local-h2 / local-docker プロファイル）。
 *
 * <p>ダッシュボードの各メニュー（荷主・見積・予約）が空でない状態になるよう、
 * ApplicationReadyEvent 後に CommandGateway 経由でサンプルデータを投入する。</p>
 *
 * <p>各データは ID 単位で projection に存在するかをチェックして idempotent に動作する。</p>
 */
@Component
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private static final String SHIPPER_ACME = "SHIP-001";
    private static final String SHIPPER_YAMADA = "SHIP-002";
    private static final String SHIPPER_TECHVISION = "SHIP-003";
    private static final String QUOTATION_ACME = "QUOT-001";
    private static final String QUOTATION_YAMADA = "QUOT-002";
    private static final String BOOKING_ACME = "BK-001";
    private static final String BOOKING_YAMADA = "BK-002";
    private static final String BOOKING_TECHVISION = "BK-003";
    private static final String CITY_TOKYO = "Tokyo";
    private static final String COUNTRY_JP = "JP";
    private static final String UNLOCODE_TYO = "JPTYO";
    private static final String UNLOCODE_LAX = "USLAX";
    private static final String CURRENCY_JPY = "JPY";

    private final CommandGateway commandGateway;
    private final ShipperMapper shipperMapper;
    private final QuotationMapper quotationMapper;
    private final CargoSummaryMapper cargoSummaryMapper;

    public DevDataSeeder(
            CommandGateway commandGateway,
            ShipperMapper shipperMapper,
            QuotationMapper quotationMapper,
            CargoSummaryMapper cargoSummaryMapper
    ) {
        this.commandGateway = commandGateway;
        this.shipperMapper = shipperMapper;
        this.quotationMapper = quotationMapper;
        this.cargoSummaryMapper = cargoSummaryMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        log.info("DevDataSeeder: seeding bookingms sample data (local-h2/local-docker)");
        try {
            seedShippers();
            seedQuotations();
            seedBookings();
            log.info("DevDataSeeder: bookingms seed complete");
        } catch (Exception e) {
            log.warn("DevDataSeeder: bookingms seed failed (continuing)", e);
        }
    }

    private void seedShippers() {
        sendIfAbsent(
                SHIPPER_ACME,
                shipperMapper.findByShipperId(SHIPPER_ACME) != null,
                new RegisterShipperCommand(
                        SHIPPER_ACME, ShipperType.CORPORATE,
                        "ACME Corporation",
                        "1-1-1 Marunouchi", null, CITY_TOKYO, COUNTRY_JP, "100-0005",
                        "logistics@acme.example.com", "+81-3-1234-5678",
                        "CN-2026-0001", new BigDecimal("0.100")
                )
        );
        sendIfAbsent(
                SHIPPER_YAMADA,
                shipperMapper.findByShipperId(SHIPPER_YAMADA) != null,
                new RegisterShipperCommand(
                        SHIPPER_YAMADA, ShipperType.INDIVIDUAL,
                        "山田太郎",
                        "2-2-2 Shibuya", null, CITY_TOKYO, COUNTRY_JP, "150-0002",
                        "yamada.taro@example.com", "+81-90-1234-5678",
                        null, null
                )
        );
        sendIfAbsent(
                SHIPPER_TECHVISION,
                shipperMapper.findByShipperId(SHIPPER_TECHVISION) != null,
                new RegisterShipperCommand(
                        SHIPPER_TECHVISION, ShipperType.CORPORATE,
                        "TechVision Inc.",
                        "3-3-3 Roppongi", "10F", CITY_TOKYO, COUNTRY_JP, "106-0032",
                        "ops@techvision.example.com", "+81-3-9876-5432",
                        "CN-2026-0002", new BigDecimal("0.050")
                )
        );
    }

    private void seedQuotations() {
        CargoSpecification generalCargo = new CargoSpecification(
                CargoType.GENERAL,
                new BigDecimal("1500.00"),
                new Dimensions(120, 80, 100),
                10,
                "Industrial equipment"
        );
        RouteSpecification spec = new RouteSpecification(
                UNLOCODE_TYO, UNLOCODE_LAX, LocalDate.now().plusDays(30)
        );
        List<RouteCandidate> candidates = List.of(
                new RouteCandidate("JPTYO → USLAX (V001+V002)", 14, new BigDecimal("250000"), CURRENCY_JPY),
                new RouteCandidate("JPTYO → SGSIN → USLAX (V001/V002)", 18, new BigDecimal("220000"), CURRENCY_JPY)
        );

        sendIfAbsent(
                QUOTATION_ACME,
                quotationMapper.findById(QUOTATION_ACME) != null,
                new CreateQuotationCommand(
                        QUOTATION_ACME, SHIPPER_ACME, spec, generalCargo,
                        candidates, new BigDecimal("250000"), CURRENCY_JPY,
                        LocalDate.now().plusDays(14)
                )
        );
        sendIfAbsent(
                QUOTATION_YAMADA,
                quotationMapper.findById(QUOTATION_YAMADA) != null,
                new CreateQuotationCommand(
                        QUOTATION_YAMADA, SHIPPER_YAMADA, spec, generalCargo,
                        candidates, new BigDecimal("180000"), CURRENCY_JPY,
                        LocalDate.now().plusDays(14)
                )
        );
    }

    private void seedBookings() {
        CargoSpecification cargo = new CargoSpecification(
                CargoType.GENERAL,
                new BigDecimal("2000.00"),
                new Dimensions(140, 90, 110),
                5,
                "Electronics components"
        );
        RouteSpecification spec = new RouteSpecification(
                UNLOCODE_TYO, UNLOCODE_LAX, LocalDate.now().plusDays(40)
        );

        sendIfAbsent(
                BOOKING_ACME,
                cargoSummaryMapper.findByBookingId(BOOKING_ACME) != null,
                new BookCargoCommand(BOOKING_ACME, SHIPPER_ACME, spec, cargo)
        );
        sendIfAbsent(
                BOOKING_YAMADA,
                cargoSummaryMapper.findByBookingId(BOOKING_YAMADA) != null,
                new BookCargoCommand(BOOKING_YAMADA, SHIPPER_YAMADA, spec, cargo)
        );
        sendIfAbsent(
                BOOKING_TECHVISION,
                cargoSummaryMapper.findByBookingId(BOOKING_TECHVISION) != null,
                new BookCargoCommand(BOOKING_TECHVISION, SHIPPER_TECHVISION, spec, cargo)
        );
    }

    private void sendIfAbsent(String id, boolean alreadyExists, Object command) {
        if (alreadyExists) {
            log.debug("DevDataSeeder: {} already exists, skipping", id);
            return;
        }
        try {
            commandGateway.send(command).get(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info("DevDataSeeder: seeded {}", id);
        } catch (Exception e) {
            log.warn("DevDataSeeder: failed to seed {}: {}", id, e.getMessage());
        }
    }
}
