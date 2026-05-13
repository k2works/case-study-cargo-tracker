// Auth Microservice ビルドスクリプト
//
// 役割: ユーザー認証・認可（JWT 発行・検証）。Event Sourcing 適用外、MyBatis + PostgreSQL で状態管理。
// アーキテクチャ: Spring Boot 4 + Spring Security 7 + jjwt 0.12 + MyBatis + Flyway

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Web + Actuator
    implementation(libs.bundles.spring.web)

    // Spring Security
    implementation(libs.spring.boot.starter.security)

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // MyBatis + JDBC
    implementation(libs.bundles.mybatis)

    // Flyway DB マイグレーション
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.database.postgresql)

    // OpenAPI / Swagger UI
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // データベース
    implementation(libs.postgresql)
    runtimeOnly(libs.h2)

    // テスト
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mybatis.spring.boot.starter.test)
    testImplementation(libs.bundles.test.integration)
    testImplementation(libs.archunit.junit5)
}

springBoot {
    mainClass.set("com.example.cargotracker.authms.AuthApplication")
}

tasks.bootJar {
    archiveFileName.set("authms.jar")
}
