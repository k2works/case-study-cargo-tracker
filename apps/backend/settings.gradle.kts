// 国際貨物輸送管理システム バックエンド マルチプロジェクト設定
//
// Phase 0: bookingms のみ動作する最小スケルトン。
// 他サービスは settings.gradle で予約だが build.gradle 未配置（.gitkeep のみ）。

rootProject.name = "cargo-tracker-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // サブプロジェクトが repositories を独自宣言した場合に失敗させ、依存解決を一元化する。
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        // Phase 1 で Spring Boot 4 / Axon 5 のマイルストーン版へ切り替える際に有効化する。
        // maven { url = uri("https://repo.spring.io/milestone") }
    }

    // gradle/libs.versions.toml は Gradle 規約により `libs` カタログとして自動ロードされる。
    // ここで `versionCatalogs { create("libs") { from(...) } }` を明示すると二重定義となり、
    // 「In version catalog libs, you can only call the 'from' method a single time.」が発生する。
}

// 実装済みサービス
include("bookingms")
include("authms")
include("gatewayms")
include("routingms")

// 雛形のみ（Phase 2 以降に実装）
// include("trackingms")
// include("handlingms")
// include("billingms")
// include("shared")
