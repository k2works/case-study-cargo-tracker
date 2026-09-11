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
    // AxonJdbcConfiguration が DataSource / PlatformTransactionManager を使う。
    compileOnly(libs.spring.boot.starter.jdbc)
    // API のエラー対応表（interfaces/rest）が使う。**compileOnly にする**——
    // Web を持たないサービスに Web の起動を持ち込まないため。
    compileOnly(libs.spring.boot.starter.web)
    // 退避したイベントを処理し直す入口（DeadLetterRetryEndpoint）が使う。
    // **compileOnly にする**——各サービスは自分で actuator を宣言している
    // （BuildConventionTest）。ここで api にすると、宣言を消しても気づけない。
    compileOnly(libs.spring.boot.starter.actuator)

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
    // jig-erd（実スキーマからの ER 図生成）。SchemaErdGenerator が使う。
    testFixturesApi(libs.jig.erd)
    testFixturesApi(libs.flyway.core)
    testFixturesApi(libs.flyway.postgresql)
    testFixturesApi(libs.postgresql)

    // 違反フィクスチャは実コードと同じ形で書く。そのために Spring / MyBatis / Axon を
    // テスト側にも入れる。最小の違反例だけだと、ここが緑でも実コードの違反を見逃す。
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.mybatis.spring.boot.starter)
    // DeadLetterRetryEndpoint の @Endpoint を読むのに要る。compileOnly の依存は
    // テスト側に伝わらないので、ここでも宣言する（無いと -Werror で落ちる）。
    testImplementation(libs.spring.boot.starter.actuator)
}

// BuildConventionTest はビルド構成のファイルそのものを読む。入力として宣言しないと
// Gradle が UP-TO-DATE と判断し、構成を壊しても検査が走らない（空振りする）。
tasks.named<Test>("test") {
    inputs.file(rootProject.file("settings.gradle.kts"))
    inputs.file(rootProject.file("gradle/libs.versions.toml"))
    // ClusterJwtSecretTest はマニフェストそのものを読む。入力として宣言しないと
    // Gradle が UP-TO-DATE と判断し、鍵を戻しても検査が走らない。
    inputs.file(rootProject.file("../../../ops/k8s/base/kustomization.yaml"))
    // AdrHasChecksTest は ADR の文書そのものを読む。入力として宣言しないと
    // Gradle が UP-TO-DATE と判断し、検査の節を消しても赤にならない（IT9 で実測）。
    inputs.dir(rootProject.file("../../../docs/adr/cargo-tracker"))
            .withPropertyName("adrDocuments")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    // ArchRulesAreAppliedTest は各サービスのテストソースを読む。
    inputs.files(rootProject.subprojects.map { it.file("src/test/java") })
            .withPropertyName("serviceTestSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.subprojects.map { it.file("build.gradle.kts") })
            .withPropertyName("subprojectBuildScripts")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    // ReplayCheckAccompaniesReactionTest は各サービスの本番ソースと ADR を読む。
    // 宣言しないと、Reaction Handler を足しても検査が走らずに緑のままになる。
    inputs.files(rootProject.subprojects.map { it.file("src/main/java") })
            .withPropertyName("serviceMainSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file(
            "../../../docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md"))
    // AcceptanceFixturesAreNotTimeBombsTest は受け入れテストのソースとシナリオを読む。
    // 宣言しないと Gradle が UP-TO-DATE と判断し、固定日付を書き足しても赤にならない。
    inputs.dir(rootProject.file("acceptance-tests/src"))
            .withPropertyName("acceptanceSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    // EventSourcedServicesHaveTheSameShapeTest は各サービスの application.yml を読む。
    // 宣言しないと、Processing Group の列挙を消しても検査が走らずに緑のままになる。
    inputs.files(rootProject.subprojects.map { it.file("src/main/resources") })
            .withPropertyName("serviceMainResources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
}
