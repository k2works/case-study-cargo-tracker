plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // **Event Sourcing は使わない**（[ADR-0020] 決定 3）。実行の記録は業務の
    // 事実ではなく、現在状態だけが要る（authms と同じ扱い）。Axon は入れるが、
    // 共有設定（起動確認・時計）が同じ形で動くためであって集約のためではない。
    implementation(libs.bundles.axon)

    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.mybatis.spring.boot.starter)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(testFixtures(project(":shared")))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)
}
