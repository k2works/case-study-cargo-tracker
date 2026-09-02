// 全サブプロジェクト共通の規約。
//
// 正典: docs/design/cargo-tracker/tech_stack.md

plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

// subprojects {} の中では libs アクセサが解決できないため、外側で捕まえておく。
val javaVersion = libs.versions.java.get().toInt()
val springBootStarterTest = libs.spring.boot.starter.test
val assertjCore = libs.assertj.core
val junitBom = libs.junit.bom
val junitPlatformLauncher = libs.junit.platform.launcher

allprojects {
    group = "com.example.cargotracker"
    version = "0.1.0-SNAPSHOT"
}

// レイヤー別のカバレッジ閾値（test_strategy.md）。
// 層ごとに変えるのは、守りたいものが層で違うから。ドメインの分岐は業務規則そのもの、
// interfaces は入出力の配線で、同じ数字で測る意味がない。
val coverageThresholds = mapOf(
    "**/domain/**" to 0.90,
    "**/application/**" to 0.85,
    "**/infrastructure/**" to 0.70,
    "**/interfaces/**" to 0.60,
)
val overallCoverageThreshold = 0.80

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(javaVersion) }
    }

    dependencies {
        "testImplementation"(springBootStarterTest)
        "testImplementation"(assertjCore)
        // Spring Boot プラグインを当てないサブプロジェクトでも JUnit Platform を起動できるように、
        // BOM とランチャーを共通で入れる。
        "testImplementation"(platform(junitBom))
        "testRuntimeOnly"(junitPlatformLauncher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // 業務タイムゾーンは Asia/Tokyo だが、テストは CI（UTC）と同じ条件でも通ること。
        // TZ を固定せず、時刻の判断は BusinessClock 経由に寄せる。
        testLogging { events("failed") }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    // 閾値は check に紐付ける。report だけ出して verify を回さないと、
    // 数字が下がっても誰も止まらない。
    tasks.named("check") { dependsOn(tasks.named("jacocoTestCoverageVerification")) }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("test"))
        violationRules {
            coverageThresholds.forEach { (pattern, minimum) ->
                rule {
                    element = "CLASS"
                    includes = listOf(pattern.replace("**/", "*.").replace("/**", ".*").replace("/", "."))
                    limit {
                        counter = "BRANCH"
                        value = "COVEREDRATIO"
                        this.minimum = minimum.toBigDecimal()
                    }
                }
            }
            rule {
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = overallCoverageThreshold.toBigDecimal()
                }
            }
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required = true
            html.required = true
        }
    }
}
