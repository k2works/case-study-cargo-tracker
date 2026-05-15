// gatewayms ビルドスクリプト
//
// Spring Cloud Gateway Server WebFlux ベースの API ゲートウェイ。
// WebFlux（Reactive）のため SpotBugs の空クラス解析をスキップ。

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // DevTools（bootRun 自動再起動・LiveReload）
    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("gatewayms.jar")
}

// gatewayms は WebFlux ベースのため SpotBugs の空クラス解析をスキップ
tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") { enabled = false }
tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest") { enabled = false }
