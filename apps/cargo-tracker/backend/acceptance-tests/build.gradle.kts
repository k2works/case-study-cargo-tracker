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
