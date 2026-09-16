plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // Axon は starter と connector を必ず同じ版で入れる（ADR-0001 決定 3）。
    // connector は starter の推移的依存に含まれないので明示する。
    implementation(libs.bundles.axon)

    testImplementation(libs.axon.test)
    testImplementation(testFixtures(project(":shared")))
}

dependencies {
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.mybatis.spring.boot.starter)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)
}

// SimulatedOriginExcludedIT は TrackingSummaryMapper の宣言そのものを読む
// （件数と一覧が同じ条件で絞っているかは、共有 DB の件数では判別できない）。
// **入力として宣言しないと** Gradle が UP-TO-DATE と判断し、宣言を壊しても
// 検査が走らない（IT10 の教訓）。
tasks.named<Test>("test") {
    inputs.file(file("src/main/java/com/example/cargotracker/tracking/"
        + "infrastructure/persistence/TrackingSummaryMapper.java"))
}
