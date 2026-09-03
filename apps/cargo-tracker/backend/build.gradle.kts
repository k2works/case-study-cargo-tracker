// 全サブプロジェクト共通の規約。
//
// 正典: docs/design/cargo-tracker/tech_stack.md

import com.github.spotbugs.snom.SpotBugsTask

plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.jig) apply false
}

// subprojects {} の中では libs アクセサが解決できないため、外側で捕まえておく。
val javaVersion = libs.versions.java.get().toInt()
val springBootStarterTest = libs.spring.boot.starter.test
val assertjCore = libs.assertj.core
val junitBom = libs.junit.bom
val junitPlatformLauncher = libs.junit.platform.launcher
val checkstyleVersion = libs.versions.checkstyle.get()
val spotbugsToolVersion = libs.versions.spotbugs.tool.get()

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
    apply(plugin = "checkstyle")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "org.dddjava.jig-gradle-plugin")

    // JIG（コードからの設計ドキュメント生成）。
    // BC ごとの構造を見たいので全パッケージを対象にする。
    extensions.configure<org.dddjava.jig.gradle.JigConfig> {
        modelPattern = ".+"
        outputDirectory = layout.buildDirectory.dir("jig").get().asFile.toString()
    }

    // 指摘でビルドを止める。警告のまま置くと、量が増えて誰も読まなくなる。
    extensions.configure<CheckstyleExtension> {
        toolVersion = checkstyleVersion
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        configDirectory = rootProject.file("config/checkstyle")
        isIgnoreFailures = false
        maxWarnings = 0
    }

    extensions.configure<com.github.spotbugs.snom.SpotBugsExtension> {
        // Java 25（クラスファイル 69）を読める版。古い版は全クラスの解析に失敗し、
        // 「1 件も指摘が無い」ように見える。
        toolVersion = spotbugsToolVersion
        excludeFilter = rootProject.file("config/spotbugs/exclude-filter.xml")
        ignoreFailures = false
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") { required = true }
        reports.create("xml") { required = false }
    }

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

    // --- jig-erd（実スキーマからの ER 図生成）---
    // ER 図生成は検証ではないため通常の test からは除外し、jigErd でのみ実行する
    // （Docker と Graphviz に依存する）。
    if (file("$projectDir/src/test/resources/jig.properties").exists()) {
        tasks.register<Test>("jigErd") {
            group = "documentation"
            description = "Flyway で構築した実スキーマから ER 図を生成する（Docker と Graphviz が必要）"
            val testSourceSet = project.extensions.getByType<JavaPluginExtension>().sourceSets["test"]
            testClassesDirs = testSourceSet.output.classesDirs
            classpath = testSourceSet.runtimeClasspath
            useJUnitPlatform()
            filter { includeTestsMatching("*.SchemaErdDocument") }
            // 生成物は毎回作り直す。UP-TO-DATE で飛ばすと、マイグレーションを足しても
            // 図が古いままになり、乖離を見つけるという目的が消える。
            outputs.upToDateWhen { false }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // 業務タイムゾーンは Asia/Tokyo だが、テストは CI（UTC）と同じ条件でも通ること。
        // TZ を固定せず、時刻の判断は BusinessClock 経由に寄せる。
        testLogging { events("failed") }
        if (name == "test") {
            filter { excludeTestsMatching("*.SchemaErdDocument") }
        }
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
