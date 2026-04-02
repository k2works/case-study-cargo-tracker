package com.example.cargotracker.booking.infrastructure.config;

import com.example.cargotracker.booking.application.internal.commandservices.RegisterBookingCommandService;
import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistencePort;
import com.example.cargotracker.booking.domain.model.commands.RegisterBookingCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
@Profile("default")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DefaultProfileBookingSeedConfiguration {

    private static final String TOKYO_UNLOCODE = "JPTYO";
    private static final String SINGAPORE_UNLOCODE = "SGSIN";

    @Bean
    @Order(1)
    public ApplicationRunner defaultProfileBookingSeedDataRunner(
            RegisterBookingCommandService registerBookingCommandService,
            BookingRepository bookingRepository,
            ShipperExistencePort shipperExistencePort) {
        return args -> {
            if (!bookingRepository.findAll().isEmpty()) {
                return;
            }

            var seedShipper = shipperExistencePort.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("シード用の荷主が存在しません。"));

            LocalDate pickupDate = LocalDate.now().plusDays(3);
            registerBookingCommandService.execute(new RegisterBookingCommand(
                    seedShipper.id(),
                    CargoType.GENERAL_CARGO,
                    new BigDecimal("1200.50"),
                    new BigDecimal("230.0"),
                    new BigDecimal("180.0"),
                    new BigDecimal("160.0"),
                    12,
                    "電子部品",
                    TOKYO_UNLOCODE,
                    SINGAPORE_UNLOCODE,
                    pickupDate,
                    pickupDate.plusDays(10),
                    null,
                    null,
                    null,
                    null
            ));
        };
    }
}
