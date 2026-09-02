// テスト専用サブプロジェクト。業務サービスの数（8）には数えない（ADR-0001）。
plugins { java }

dependencies {
    testImplementation(project(":shared"))
    testImplementation(libs.axon.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)
}

dependencies {
    // Cucumber は 3 つの成果物を同一バージョンで揃える（tech_stack.md）。
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.rest.assured)
}
