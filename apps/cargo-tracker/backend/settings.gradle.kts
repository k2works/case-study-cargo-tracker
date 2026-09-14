// 国際貨物輸送管理システム（CQRS / ES 版）バックエンド
//
// 正典: docs/design/cargo-tracker/architecture_backend.md
//       docs/adr/cargo-tracker/0001-cqrs-es-with-axon-in-microservices.md
//
// 業務サブプロジェクトは shared + 8 サービスの 9 つ。
// simulationms は業務そのものではなく「業務が成立していることを確かめる手段」だが、
// デプロイ単位としては同じ扱いにする（[ADR-0020]）。
// contract-tests / acceptance-tests はテスト専用で、この 8 つには数えない（ADR-0001）。

rootProject.name = "cargo-tracker-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // サブプロジェクトが repositories を独自宣言したら失敗させ、依存解決を一元化する。
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
    // gradle/libs.versions.toml は Gradle 規約により libs カタログとして自動ロードされる。
}

// 業務サブプロジェクト（9 つ）
include("shared")
include("gatewayms")
include("authms")
include("bookingms")
include("routingms")
include("trackingms")
include("handlingms")
include("billingms")
include("simulationms")

// テスト専用サブプロジェクト（業務サービスの数には含めない）
include("contract-tests")
include("acceptance-tests")
