// 契約テスト（ゴールデン JSON と往復）。業務サービスの数（8）には数えない（ADR-0001）。
plugins { java }

dependencies {
    testImplementation(project(":shared"))
    testImplementation(libs.axon.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.awaitility)
    testImplementation(libs.assertj.core)
    // 契約の名簿はパッケージ走査で導出する（手書きにすると載せ忘れが素通りする）。
    testImplementation(libs.archunit.junit5)
}
