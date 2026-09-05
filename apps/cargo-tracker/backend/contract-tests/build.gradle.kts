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

// 契約の往復（2 サービス起動）。ゴールデンの一致だけでは「実際に届く」ことを
// 見ていない（IT2 引き継ぎ 1）。ゴールデンの検査とはクラスパスを分ける。
// 往復側は 2 つのサービスを載せるので重く、ゴールデンの検査まで巻き込みたくない。
val roundTripTest: SourceSet by sourceSets.creating

dependencies {
    "roundTripTestImplementation"(project(":shared"))
    "roundTripTestImplementation"(testFixtures(project(":shared")))
    "roundTripTestImplementation"(project(":bookingms"))
    "roundTripTestImplementation"(project(":billingms"))
    // 契約クエリの往復（US08）。bookingms → routingms を同じ JVM で確かめる。
    "roundTripTestImplementation"(project(":routingms"))
    "roundTripTestImplementation"(libs.spring.boot.starter.test)
    "roundTripTestImplementation"(libs.spring.boot.starter.web)
    "roundTripTestImplementation"(libs.spring.boot.starter.jdbc)
    "roundTripTestImplementation"(libs.testcontainers.junit.jupiter)
    "roundTripTestImplementation"(libs.testcontainers.postgresql)
    "roundTripTestImplementation"(libs.awaitility)
    "roundTripTestImplementation"(libs.assertj.core)
    "roundTripTestImplementation"(libs.axon.test)
    "roundTripTestRuntimeOnly"(libs.junit.platform.launcher)
}

val contractRoundTripTest = tasks.register<Test>("contractRoundTripTest") {
    description = "契約イベントが実際に別サービスへ届くことを見る"
    group = "verification"
    testClassesDirs = roundTripTest.output.classesDirs
    classpath = roundTripTest.runtimeClasspath
    useJUnitPlatform()
}

// `./gradlew :contract-tests:test` で両方回る。片方だけ回ると、往復の検査が
// 黙って走らなくなる。
tasks.named("test") { dependsOn(contractRoundTripTest) }
