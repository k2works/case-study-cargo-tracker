package com.example.bookingms.config;

import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.port.ShipperRepository;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingConfig {

    /** 業務日付は業務タイムゾーンで判断する。UTC で判断すると「当日」の扱いがずれる時間帯ができる。 */
    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }

    @Bean
    public RegisterShipperUseCase registerShipperUseCase(ShipperRepository repository) {
        return new RegisterShipperUseCase(repository);
    }
}
