package com.example.cargotracker.shared.infrastructure.config;

import com.example.cargotracker.booking.application.internal.commandservices.RegisterBookingCommandService;
import com.example.cargotracker.booking.domain.model.commands.RegisterBookingCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
@Profile("default")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DefaultProfileSeedDataConfiguration {

    @Bean
    public ApplicationRunner defaultProfileSeedDataRunner(
            RegisterShipperCommandService registerShipperCommandService,
            RegisterBookingCommandService registerBookingCommandService,
            ShipperRepository shipperRepository,
            BookingRepository bookingRepository) {
        return args -> {
            if (shipperRepository.findAll().isEmpty()) {
                registerShipperCommandService.execute(new RegisterShipperCommand(
                        "山田 太郎",
                        "taro.yamada@example.com",
                        "090-1111-2222",
                        CustomerCategory.INDIVIDUAL,
                        null,
                        null
                ));
                registerShipperCommandService.execute(new RegisterShipperCommand(
                        "海運商事株式会社",
                        "sales@kaiun.example.com",
                        "03-1234-5678",
                        CustomerCategory.CORPORATE,
                        "CORP-2026-001",
                        new BigDecimal("0.10")
                ));
            }

            if (bookingRepository.findAll().isEmpty()) {
                ShipperId seedShipperId = shipperRepository.findAll().stream()
                        .findFirst()
                        .map(shipper -> shipper.getId())
                        .orElseThrow(() -> new IllegalStateException("シード用の荷主が存在しません。"));

                LocalDate pickupDate = LocalDate.now().plusDays(3);
                registerBookingCommandService.execute(new RegisterBookingCommand(
                        seedShipperId.value(),
                        CargoType.GENERAL_CARGO,
                        new BigDecimal("1200.50"),
                        new BigDecimal("230.0"),
                        new BigDecimal("180.0"),
                        new BigDecimal("160.0"),
                        12,
                        "電子部品",
                        "東京港",
                        "シンガポール港",
                        pickupDate,
                        pickupDate.plusDays(10)
                ));
            }
        };
    }
}
