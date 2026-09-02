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

subprojects {
    apply(plugin = "java")

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
}
