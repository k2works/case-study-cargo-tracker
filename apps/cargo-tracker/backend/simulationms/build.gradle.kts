plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // **Axon は入れない**（[ADR-0020] 決定 3 / IT16 のレビュー N7）。集約も
    // イベントも持たないので、要るのは共有の時計だけである。入れておくと
    // 起動確認が Axon Server を待ち、**切り分けの道具が切り分けたい相手より
    // 先に落ちる**。`UsesNoAxonTest` が参照ゼロを固定している。

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

// SimulationPropertiesTest は application.yml を読む。**入力として宣言しないと**
// Gradle が UP-TO-DATE と判断し、設定を壊しても検査が走らない（IT10 の教訓）。
tasks.named<Test>("test") {
    inputs.file(file("src/main/resources/application.yml"))
}
