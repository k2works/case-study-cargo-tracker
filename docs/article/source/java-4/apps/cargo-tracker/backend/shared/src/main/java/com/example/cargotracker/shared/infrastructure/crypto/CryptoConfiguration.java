package com.example.cargotracker.shared.infrastructure.crypto;

import java.nio.file.Path;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    /**
     * Axon の Converter を差し替え、個人情報をシリアライズ時に暗号化する（ADR-0003 決定 1）。
     *
     * <p>ここに置くことで、ドメインは平文で判断し、Event Store には暗号文だけが入る。
     * Controller や集約で暗号化すると、暗号文が値オブジェクトの検査に落ちる
     * （IT1 タスク 6.5 で実際に落ちた）。</p>
     */
    @Bean
    @Primary
    public Converter converter(ShipperDataCipher cipher) {
        return new ShipperDataEncryptingConverter(new JacksonConverter(), cipher);
    }
}
