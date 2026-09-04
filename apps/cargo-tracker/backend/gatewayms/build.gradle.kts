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

    implementation(libs.spring.cloud.starter.gateway.server.webmvc)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(libs.axon.test)
    testImplementation(libs.mockito.core)
    testImplementation(testFixtures(project(":shared")))
}

tasks.named<Test>("test") {
    // EveryServiceEndpointIsRoutedAndProtectedTest は各サービスの本番ソースと
    // Gateway のルート定義を読む。入力として宣言しないと Gradle が UP-TO-DATE と
    // 判断し、経路を足しても検査が走らない。
    inputs.files(rootProject.subprojects.map { it.file("src/main/java") })
            .withPropertyName("serviceMainSources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(file("src/main/resources/application.yml"))
}
