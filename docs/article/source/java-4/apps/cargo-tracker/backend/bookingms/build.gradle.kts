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
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.awaitility)
}

// 依存ミドルウェアを止めるテストは別タスクに隔離する。同じ JVM で回すと、
// 止めた影響が後のテストに出て「たまに落ちる」ようになり、実行順で結果が変わる。
// 原因がこのテストにあることに気づきにくいので、最初から分けておく。
val outageTest = tasks.register<Test>("outageTest") {
    description = "Axon Server を止める統合テスト（デモ項目 5）"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*AxonServerOutageIT") }
}

tasks.named<Test>("test") {
    filter { excludeTestsMatching("*AxonServerOutageIT") }
}

tasks.named("check") { dependsOn(outageTest) }
