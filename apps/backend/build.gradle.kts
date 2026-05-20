// 国際貨物輸送管理システム ルートビルドスクリプト
//
// ルートプロジェクトには java プラグインを適用しない（ルートにソースを置かないため）。
// サブプロジェクトに共通設定を流し込み、サービス固有設定は各 build.gradle.kts に記述する。

import org.gradle.api.plugins.JavaPluginExtension
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.spotbugs) apply false
}

sonar {
    properties {
        property("sonar.projectKey", "cargo-tracker-backend")
        property("sonar.projectName", "Backend")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.token", System.getenv("SONAR_TOKEN") ?: "")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.coverage.jacoco.xmlReportPaths", "**/build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.coverage.exclusions",
            "**/dto/**,**/*Record.java,**/*Request.java,**/*Response.java," +
            "**/events/**,**/*Event.java,**/*Command.java," +
            "**/config/**,**/*Configuration.java,**/*Config.java," +
            "**/*Application.java"
        )
    }
}

allprojects {
    group = "com.example.cargotracker"
    version = "0.1.0-SNAPSHOT"

    // repositories はここでは宣言しない。
    // settings.gradle.kts の dependencyResolutionManagement.repositories に一元化されており、
    // FAIL_ON_PROJECT_REPOS が有効なため、プロジェクト側で宣言すると build が失敗する。
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "com.github.spotbugs")

    // `subprojects { java { ... } }` の型付きアクセサは Kotlin DSL では解決できないため
    // `configure<JavaPluginExtension>` で明示的に拡張に触れる。
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    // Checkstyle 設定
    configure<CheckstyleExtension> {
        toolVersion = "10.21.4"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
        maxWarnings = 0
    }

    // SpotBugs 設定
    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        toolVersion = "4.9.8"
        excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
        ignoreFailures = false
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") { required.set(true) }
        reports.create("xml") { required.set(true) }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }
}
