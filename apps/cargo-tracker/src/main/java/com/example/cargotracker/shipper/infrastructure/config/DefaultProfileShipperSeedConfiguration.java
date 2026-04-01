package com.example.cargotracker.shipper.infrastructure.config;

import com.example.cargotracker.shipper.application.internal.commandservices.RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;

@Configuration
@Profile("default")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DefaultProfileShipperSeedConfiguration {

    @Bean
    @Order(0)
    public ApplicationRunner defaultProfileShipperSeedDataRunner(
            RegisterShipperCommandService registerShipperCommandService,
            ShipperRepository shipperRepository) {
        return args -> {
            if (!shipperRepository.findAll().isEmpty()) {
                return;
            }

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
        };
    }
}
