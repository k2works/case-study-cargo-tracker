// 共有カーネル。置けるパッケージの名簿は ADR-0001 のコンプライアンスで固定する。
// Spring Boot アプリケーションではないので bootJar は作らない。
plugins {
    `java-library`
    // ArchUnit のルールを全サービスへ配る。
    `java-test-fixtures`
}

dependencies {
    api(libs.axon.spring.boot.starter)
    api(libs.spring.boot.starter.validation)
    // 起動時接続検査が AxonServerConnectionManager / AxonServerConnection を使う。
    // 各サービスも ADR-0001 決定 3 に従って明示依存を持つ（BuildConventionTest で固定）。
    api(libs.axon.server.connector)

    // testFixtures 側でルールを組み立てる。各サービスは testFixtures(project(":shared")) で取り込む。
    testFixturesApi(libs.archunit.junit5)
    testFixturesApi(libs.assertj.core)
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter)
    // 統合テストの基底クラス（Axon Server（DCB 有効）+ PostgreSQL）。
    testFixturesApi(libs.axon.test)
    testFixturesApi(libs.testcontainers.junit.jupiter)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.awaitility)

    // 違反フィクスチャは実コードと同じ形で書く。そのために Spring / MyBatis / Axon を
    // テスト側にも入れる。最小の違反例だけだと、ここが緑でも実コードの違反を見逃す。
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.mybatis.spring.boot.starter)
}

// BuildConventionTest はビルド構成のファイルそのものを読む。入力として宣言しないと
// Gradle が UP-TO-DATE と判断し、構成を壊しても検査が走らない（空振りする）。
tasks.named<Test>("test") {
    inputs.file(rootProject.file("settings.gradle.kts"))
    inputs.file(rootProject.file("gradle/libs.versions.toml"))
    // ArchRulesAreAppliedTest は各サービスのテストソースを読む。
    inputs.files(rootProject.subprojects.map { it.file("src/test/java") })
            .withPropertyName("serviceTestSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.subprojects.map { it.file("build.gradle.kts") })
            .withPropertyName("subprojectBuildScripts")
            .withPathSensitivity(PathSensitivity.RELATIVE)
}
