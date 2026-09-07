// 受け入れテスト（デモ項目の Gherkin 実行）。業務サービスの数（8）には数えない（ADR-0001）。
plugins { java }

dependencies {
    testImplementation(project(":shared"))
    testImplementation(testFixtures(project(":shared")))
    testImplementation(libs.axon.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)

    // Cucumber は 3 つの成果物を同一バージョンで揃える（tech_stack.md）。
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.rest.assured)
    testImplementation(libs.assertj.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.platform:junit-platform-suite")

    // 起動するのは対象サービスだけにする。複数サービスを同一 JVM に載せると、
    // 各サービスの V001 マイグレーションが classpath 上で衝突する
    // （Found more than one migration with version 001）。サービスを跨ぐデモ項目は
    // サービスごとに別のコンテキストで回す。
    testImplementation(project(":bookingms"))
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.jdbc)
    // ステップ定義が Mapper を注入するので、注釈の定義もクラスパスに要る。
    testImplementation(libs.mybatis.spring.boot.starter)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

// routingms の受け入れテストは別のソースセットに置く。
// Cucumber は 1 つの glue パッケージにつき 1 つのコンテキストしか持てず、
// 起動するサービスが違えばコンテキストも別になる。ソースセットを分けることで、
// 各スイートのクラスパスにそのサービスの分だけを載せる。
//
// マイグレーションの衝突（双方の V001）は db/migration/<サービス名>/ へ分けた
// ことで解けているので、ここは分離の理由ではない。
val routingTest: SourceSet by sourceSets.creating

dependencies {
    "routingTestImplementation"(project(":shared"))
    "routingTestImplementation"(testFixtures(project(":shared")))
    "routingTestImplementation"(project(":routingms"))
    "routingTestImplementation"(libs.axon.test)
    "routingTestImplementation"(libs.testcontainers.junit.jupiter)
    "routingTestImplementation"(libs.testcontainers.postgresql)
    "routingTestImplementation"(libs.awaitility)
    "routingTestImplementation"(libs.cucumber.java)
    "routingTestImplementation"(libs.cucumber.spring)
    "routingTestImplementation"(libs.cucumber.junit.platform.engine)
    "routingTestImplementation"(libs.assertj.core)
    "routingTestImplementation"(platform(libs.junit.bom))
    "routingTestImplementation"("org.junit.platform:junit-platform-suite")
    "routingTestImplementation"(libs.spring.boot.starter.test)
    "routingTestImplementation"(libs.spring.boot.starter.web)
    "routingTestImplementation"(libs.spring.boot.starter.jdbc)
    "routingTestImplementation"(libs.mybatis.spring.boot.starter)
    "routingTestRuntimeOnly"(libs.junit.platform.launcher)
}

val routingAcceptanceTest = tasks.register<Test>("routingAcceptanceTest") {
    description = "航海スケジュール（routingms）のデモ項目を回す"
    group = "verification"
    testClassesDirs = routingTest.output.classesDirs
    classpath = routingTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

// 追跡（trackingms）も同じ理由で別のソースセットに置く。
val trackingTest: SourceSet by sourceSets.creating

dependencies {
    "trackingTestImplementation"(project(":shared"))
    "trackingTestImplementation"(testFixtures(project(":shared")))
    "trackingTestImplementation"(project(":trackingms"))
    "trackingTestImplementation"(libs.axon.test)
    "trackingTestImplementation"(libs.testcontainers.junit.jupiter)
    "trackingTestImplementation"(libs.testcontainers.postgresql)
    "trackingTestImplementation"(libs.awaitility)
    "trackingTestImplementation"(libs.cucumber.java)
    "trackingTestImplementation"(libs.cucumber.spring)
    "trackingTestImplementation"(libs.cucumber.junit.platform.engine)
    "trackingTestImplementation"(libs.assertj.core)
    "trackingTestImplementation"(platform(libs.junit.bom))
    "trackingTestImplementation"("org.junit.platform:junit-platform-suite")
    "trackingTestImplementation"(libs.spring.boot.starter.test)
    "trackingTestImplementation"(libs.spring.boot.starter.web)
    "trackingTestImplementation"(libs.spring.boot.starter.jdbc)
    "trackingTestImplementation"(libs.mybatis.spring.boot.starter)
    "trackingTestRuntimeOnly"(libs.junit.platform.launcher)
}

val trackingAcceptanceTest = tasks.register<Test>("trackingAcceptanceTest") {
    description = "追跡の照会と状態の手動更新（trackingms）のデモ項目を回す"
    group = "verification"
    testClassesDirs = trackingTest.output.classesDirs
    classpath = trackingTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

// `./gradlew :acceptance-tests:test` で全部回る。片方だけ回ると、
// 増えたサービスの受け入れが黙って走らなくなる。
// 荷役（handlingms）も同じ理由で別のソースセットに置く。
val handlingTest: SourceSet by sourceSets.creating

dependencies {
    "handlingTestImplementation"(project(":shared"))
    "handlingTestImplementation"(testFixtures(project(":shared")))
    "handlingTestImplementation"(project(":handlingms"))
    "handlingTestImplementation"(libs.axon.test)
    "handlingTestImplementation"(libs.testcontainers.junit.jupiter)
    "handlingTestImplementation"(libs.testcontainers.postgresql)
    "handlingTestImplementation"(libs.awaitility)
    "handlingTestImplementation"(libs.cucumber.java)
    "handlingTestImplementation"(libs.cucumber.spring)
    "handlingTestImplementation"(libs.cucumber.junit.platform.engine)
    "handlingTestImplementation"(libs.assertj.core)
    "handlingTestImplementation"(platform(libs.junit.bom))
    "handlingTestImplementation"("org.junit.platform:junit-platform-suite")
    "handlingTestImplementation"(libs.spring.boot.starter.test)
    "handlingTestImplementation"(libs.spring.boot.starter.web)
    "handlingTestImplementation"(libs.spring.boot.starter.jdbc)
    "handlingTestImplementation"(libs.mybatis.spring.boot.starter)
    "handlingTestRuntimeOnly"(libs.junit.platform.launcher)
}

val handlingAcceptanceTest = tasks.register<Test>("handlingAcceptanceTest") {
    description = "荷役の記録（handlingms）のデモ項目を回す"
    group = "verification"
    testClassesDirs = handlingTest.output.classesDirs
    classpath = handlingTest.runtimeClasspath
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

tasks.named("test") {
    dependsOn(routingAcceptanceTest, trackingAcceptanceTest, handlingAcceptanceTest)
}
