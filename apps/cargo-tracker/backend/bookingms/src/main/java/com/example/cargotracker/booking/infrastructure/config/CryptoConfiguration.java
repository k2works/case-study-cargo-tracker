package com.example.cargotracker.booking.infrastructure.config;

import com.example.cargotracker.booking.application.port.ShipperKeyRepository;
import com.example.cargotracker.booking.infrastructure.crypto.LocalFileShipperKeyRepository;
import com.example.cargotracker.booking.infrastructure.crypto.ShipperDataCipher;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** crypto-shredding の配線（ADR-0003）。本番は KMS の実装に差し替える。 */
@Configuration
public class CryptoConfiguration {

    @Bean
    public ShipperKeyRepository shipperKeyRepository(
            @Value("${cargo-tracker.crypto.key-directory:./.keys/shipper}") String directory) {
        return new LocalFileShipperKeyRepository(Path.of(directory));
    }

    @Bean
    public ShipperDataCipher shipperDataCipher(ShipperKeyRepository keys) {
        return new ShipperDataCipher(keys);
    }
}
