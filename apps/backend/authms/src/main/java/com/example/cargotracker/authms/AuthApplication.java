package com.example.cargotracker.authms;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 認証マイクロサービス（authms）エントリポイント。
 *
 * <p>JWT 発行・検証、ユーザー登録・ログインを提供する。
 * Event Sourcing は適用しない。MyBatis + PostgreSQL で状態を管理する。
 */
@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Cargo Tracker Auth Service API",
        version = "1.0.0",
        description = "国際貨物輸送管理システム 認証マイクロサービス（Phase 1）",
        contact = @Contact(name = "Cargo Tracker Team"),
        license = @License(name = "Apache-2.0")
    )
)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
