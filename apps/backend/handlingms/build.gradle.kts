// Handling Microservice ビルドスクリプト
//
// 役割: 荷役コンテキスト（HandlingActivity Aggregate）の Command/Event/Query Side を提供
// アーキテクチャ: Axon 5 (Command/Event Sourcing) + MyBatis (Read Side)
// 関連 ADR: ADR-0012 handlingms と trackingms の責務分離

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

    // OpenAPI / Swagger UI
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // データベース
    implementation(libs.postgresql)
    runtimeOnly(libs.h2)

    // DevTools
    developmentOnly(libs.spring.boot.devtools)

    // テスト
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.axon.test)
    testImplementation(libs.mybatis.spring.boot.starter.test)
    testImplementation(libs.bundles.test.integration)
    testImplementation(libs.archunit.junit5)
}

springBoot {
    mainClass.set("com.example.cargotracker.handlingms.HandlingApplication")
}

tasks.bootJar {
    archiveFileName.set("handlingms.jar")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// PIT 変異テスト設定（test_strategy.md L334 のドメイン層主指標 75%）
pitest {
    junit5PluginVersion.set(libs.versions.pitest.junit5.get())
    targetClasses.set(listOf(
        "com.example.cargotracker.handlingms.domain.*"
    ))
    targetTests.set(listOf(
        "com.example.cargotracker.handlingms.domain.*"
    ))
    excludedClasses.set(listOf(
        "com.example.cargotracker.handlingms.domain.model.commands.*",
        "com.example.cargotracker.handlingms.domain.model.events.*"
    ))
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(0)
    coverageThreshold.set(0)
    timeoutFactor.set(2.0.toBigDecimal())
}
