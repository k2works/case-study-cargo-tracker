// Booking Microservice ビルドスクリプト
//
// 役割: 貨物予約コンテキスト（Cargo Aggregate）の Command/Event/Query Side を提供
// アーキテクチャ: Axon 5 (Command/Event Sourcing) + MyBatis (Read Side)

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Web + Actuator
    implementation(libs.bundles.spring.web)

    // Axon Framework
    implementation(libs.axon.spring.boot.starter)

    // MyBatis + JDBC
    implementation(libs.bundles.mybatis)

    // Flyway
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)

    // Spring Security
    implementation(libs.spring.boot.starter.security)
    testImplementation(libs.spring.security.test)

    // OpenAPI / Swagger UI（/swagger-ui.html・/v3/api-docs を自動公開）
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // データベース
    implementation(libs.postgresql)
    runtimeOnly(libs.h2)

    // テスト
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.axon.test)
    testImplementation(libs.mybatis.spring.boot.starter.test)
    testImplementation(libs.bundles.test.integration)
    testImplementation(libs.archunit.junit5)
}

springBoot {
    mainClass.set("com.example.cargotracker.bookingms.BookingApplication")
}

tasks.bootJar {
    archiveFileName.set("bookingms.jar")
}
