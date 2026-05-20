package com.example.cargotracker.bookingms.infrastructure.seed;

import com.example.cargotracker.bookingms.application.ShipperCommandService;
import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingId;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.bookingms.domain.model.valueobjects.TemperatureCondition;
import com.example.cargotracker.bookingms.domain.ports.ShipperRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Heroku プロファイル起動時にサンプルシードデータを投入する {@link ApplicationRunner}。
 *
 * <p>荷主 5 件・予約 5 件を登録する。既に登録済みの場合はスキップして
 * 二重登録を防ぐ。</p>
 */
@Component
@Profile("heroku")
public class HerokuDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(HerokuDataSeeder.class);

    private final ShipperCommandService shipperCommandService;
    private final ShipperRepository shipperRepository;
    private final CommandGateway commandGateway;

    public HerokuDataSeeder(
            ShipperCommandService shipperCommandService,
            ShipperRepository shipperRepository,
            CommandGateway commandGateway) {
        this.shipperCommandService = shipperCommandService;
        this.shipperRepository = shipperRepository;
        this.commandGateway = commandGateway;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOG.info("[seed] Heroku シードデータ投入開始");
        var shipperIds = seedShippers();
        seedBookings(shipperIds);
        LOG.info("[seed] Heroku シードデータ投入完了");
    }

    private List<Long> seedShippers() {
        record ShipperDef(String name, String email, String type) {}

        var defs = List.of(
                new ShipperDef("東京海運株式会社", "info@tokyo-kaiu.example.com", "CORPORATE"),
                new ShipperDef("大阪貿易商事", "trade@osaka-boeki.example.com", "CORPORATE"),
                new ShipperDef("横浜インターナショナル", "contact@yokohama-intl.example.com", "CORPORATE"),
                new ShipperDef("田中物流", "tanaka@tanaka-butsuryu.example.com", "INDIVIDUAL"),
                new ShipperDef("鈴木商会", "suzuki@suzuki-shokai.example.com", "INDIVIDUAL")
        );

        return defs.stream().map(def -> {
            // 既存チェック（メールアドレスで判定）
            var existing = shipperRepository.findByEmail(def.email());
            if (existing.isPresent()) {
                LOG.info("[seed] 荷主スキップ（既存）: {}", def.name());
                return existing.get().getId();
            }
            try {
                var shipper = shipperCommandService.register(new ShipperCommandService.RegisterShipperCommand(
                        def.name(), def.email(), null, def.type(), null, null));
                LOG.info("[seed] 荷主登録: {} id={}", def.name(), shipper.getId());
                return shipper.getId();
            } catch (Exception e) {
                LOG.warn("[seed] 荷主登録スキップ: {} reason={}", def.name(), e.getMessage());
                return null;
            }
        }).toList();
    }

    private void seedBookings(List<Long> shipperIds) {
        record BookingDef(int shipperIndex, CargoSpecification cargoSpec, RouteSpecification routeSpec) {}

        var defs = List.of(
                new BookingDef(0,
                        CargoSpecification.general(new BigDecimal("500"), null, 10, "電子部品"),
                        new RouteSpecification(Location.of("JPTYO"), Location.of("DEHAM"),
                                LocalDate.of(2026, 12, 31))),
                new BookingDef(1,
                        new CargoSpecification(CargoType.REFRIGERATED, new BigDecimal("200"), null, 5,
                                "冷凍食品", null,
                                new TemperatureCondition(new BigDecimal("-20"), new BigDecimal("-10"))),
                        new RouteSpecification(Location.of("JPOSA"), Location.of("USNYC"),
                                LocalDate.of(2026, 11, 30))),
                new BookingDef(2,
                        new CargoSpecification(CargoType.HAZARDOUS, new BigDecimal("100"), null, 2,
                                "化学薬品",
                                new HazardInfo("3", "UN1203", "引火性液体"), null),
                        new RouteSpecification(Location.of("JPYOK"), Location.of("SGSIN"),
                                LocalDate.of(2026, 10, 31))),
                new BookingDef(3,
                        CargoSpecification.general(new BigDecimal("50"), null, 3, "衣料品"),
                        new RouteSpecification(Location.of("JPTYO"), Location.of("GBLON"),
                                LocalDate.of(2026, 9, 30))),
                new BookingDef(4,
                        CargoSpecification.general(new BigDecimal("300"), null, 8, "自動車部品"),
                        new RouteSpecification(Location.of("JPNGO"), Location.of("DEHAM"),
                                LocalDate.of(2026, 8, 31)))
        );

        for (var def : defs) {
            Long shipperId = shipperIds.get(def.shipperIndex());
            if (shipperId == null) {
                LOG.warn("[seed] 予約スキップ（対応荷主 ID なし）: index={}", def.shipperIndex());
                continue;
            }
            try {
                var command = new BookCargoCommand(
                        BookingId.generate().value(),
                        new ShipperId(shipperId),
                        def.cargoSpec(),
                        def.routeSpec());
                commandGateway.sendAndWait(command);
                LOG.info("[seed] 予約登録: {} {}→{}", def.cargoSpec().productName(),
                        def.routeSpec().origin().unLocode().value(),
                        def.routeSpec().destination().unLocode().value());
            } catch (Exception e) {
                LOG.warn("[seed] 予約登録スキップ: {} reason={}", def.cargoSpec().productName(), e.getMessage());
            }
        }
    }
}
