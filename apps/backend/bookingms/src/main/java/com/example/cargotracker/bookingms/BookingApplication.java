package com.example.cargotracker.bookingms;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 貨物予約マイクロサービス（bookingms）エントリポイント。
 *
 * <p>Phase 0 の Walking Skeleton として、Axon + MyBatis + Actuator + Swagger UI の
 * 起動確認のみ行う。Cargo Aggregate / Command Handler / Projection の実装は
 * Phase 1 で行う。
 */
@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Cargo Tracker Booking Service API",
        version = "0.1.0",
        description = "国際貨物輸送管理システム 貨物予約マイクロサービス（Phase 0 Walking Skeleton）",
        contact = @Contact(name = "Cargo Tracker Team"),
        license = @License(name = "Apache-2.0")
    )
)
public class BookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
