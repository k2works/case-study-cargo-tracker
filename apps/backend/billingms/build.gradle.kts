// Billing Microservice ビルドスクリプト
//
// 役割: 精算コンテキスト（Invoice Aggregate）の Command/Event/Query Side を提供
// アーキテクチャ: Axon 5 (Command/Event Sourcing) + MyBatis (Read Side)
// 関連: iteration_plan-8.md / US21-US23

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
    implementation(libs.axon.server.connector)

    // MyBatis + JDBC
    implementation(libs.bundles.mybatis)

    // Flyway
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
    mainClass.set("com.example.cargotracker.billingms.BillingApplication")
}

tasks.bootJar {
    archiveFileName.set("billingms.jar")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

pitest {
    junit5PluginVersion.set(libs.versions.pitest.junit5.get())
    targetClasses.set(listOf(
        "com.example.cargotracker.billingms.domain.*"
    ))
    targetTests.set(listOf(
        "com.example.cargotracker.billingms.domain.*"
    ))
    excludedClasses.set(listOf(
        "com.example.cargotracker.billingms.domain.model.commands.*",
        "com.example.cargotracker.billingms.domain.model.events.*"
    ))
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(0)
    coverageThreshold.set(0)
    timeoutFactor.set(2.0.toBigDecimal())
}
