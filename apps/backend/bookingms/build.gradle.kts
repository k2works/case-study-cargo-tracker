// Booking Microservice ビルドスクリプト
//
// 役割: 貨物予約コンテキスト（Cargo Aggregate）の Command/Event/Query Side を提供
// アーキテクチャ: Axon 5 (Command/Event Sourcing) + MyBatis (Read Side)

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.pitest)
    jacoco
}

dependencies {
    // Shared Kernel（Event クラス共有）
    implementation(project(":shared"))

    // Spring Web + Actuator
    implementation(libs.bundles.spring.web)

    // Axon Framework
    implementation(libs.axon.spring.boot.starter)
    // Axon Server 接続 connector（ADR-0009: starter 5.x からは別 artifact 切り出し）
    implementation(libs.axon.server.connector)

    // MyBatis + JDBC
    implementation(libs.bundles.mybatis)

    // Flyway（PostgreSQL ドライバはランタイムのみ。local-h2 では H2 を使うため不要）
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // Spring Security
    implementation(libs.spring.boot.starter.security)
    testImplementation(libs.spring.security.test)

    // OpenAPI / Swagger UI（/swagger-ui.html・/v3/api-docs を自動公開）
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // データベース
    implementation(libs.postgresql)
    runtimeOnly(libs.h2)

    // DevTools（bootRun 自動再起動・LiveReload）
    developmentOnly(libs.spring.boot.devtools)

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

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// PIT 変異テスト設定（ADR / test_strategy.md L334 のドメイン層主指標 75%）
// IT3 タスク 5.1：計測のみを必須とし、CI ブロックは IT4 以降で検討
pitest {
    junit5PluginVersion.set(libs.versions.pitest.junit5.get())
    targetClasses.set(listOf(
        "com.example.cargotracker.bookingms.domain.*"
    ))
    targetTests.set(listOf(
        "com.example.cargotracker.bookingms.domain.*"
    ))
    excludedClasses.set(listOf(
        // commands / events は records なので変異対象から除外（DTO 相当）
        "com.example.cargotracker.bookingms.domain.model.commands.*",
        "com.example.cargotracker.bookingms.domain.model.events.*"
    ))
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(0)            // IT3 は計測のみ。実際の数値は report で確認する
    coverageThreshold.set(0)
    timeoutFactor.set(2.0.toBigDecimal())
}
