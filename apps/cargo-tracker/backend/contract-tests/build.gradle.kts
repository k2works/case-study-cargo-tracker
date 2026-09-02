// テスト専用サブプロジェクト。業務サービスの数（8）には数えない（ADR-0001）。
plugins { java }

dependencies {
    testImplementation(project(":shared"))
    testImplementation(libs.axon.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)
}
