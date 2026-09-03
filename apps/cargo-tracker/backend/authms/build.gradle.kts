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
    implementation(libs.spring.boot.starter.security)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    implementation(libs.mybatis.spring.boot.starter)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)
}

// DemoAccountsMatchSeedTest は画面の一覧（TypeScript）とシードの SQL を読む。
// 入力として宣言しないと Gradle が UP-TO-DATE と判断し、片方だけ直しても
// 検査が走らない（空振りする）。
tasks.named<Test>("test") {
    inputs.file(rootProject.file("../frontend/src/features/auth/demoAccounts.ts"))
    inputs.file(file("src/main/resources/db/seed/R__demo_users.sql"))
}
