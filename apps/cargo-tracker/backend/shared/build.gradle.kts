// 共有カーネル。置けるパッケージの名簿は ADR-0001 のコンプライアンスで固定する。
// Spring Boot アプリケーションではないので bootJar は作らない。
plugins { `java-library` }

dependencies {
    api(libs.axon.spring.boot.starter)
    api(libs.spring.boot.starter.validation)
    // 起動時接続検査が AxonServerConnectionManager / AxonServerConnection を使う。
    // 各サービスも ADR-0001 決定 3 に従って明示依存を持つ（BuildConventionTest で固定）。
    api(libs.axon.server.connector)
}

// BuildConventionTest はビルド構成のファイルそのものを読む。入力として宣言しないと
// Gradle が UP-TO-DATE と判断し、構成を壊しても検査が走らない（空振りする）。
tasks.named<Test>("test") {
    inputs.file(rootProject.file("settings.gradle.kts"))
    inputs.file(rootProject.file("gradle/libs.versions.toml"))
    inputs.files(rootProject.subprojects.map { it.file("build.gradle.kts") })
            .withPropertyName("subprojectBuildScripts")
            .withPathSensitivity(PathSensitivity.RELATIVE)
}
