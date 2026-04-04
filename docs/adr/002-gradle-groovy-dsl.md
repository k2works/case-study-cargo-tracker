# ADR-002: ビルドツールに Gradle 9.x (Groovy DSL) を採用する

ビルドツールとして Gradle 9.2.1 を Groovy DSL で使用する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

プロジェクトのビルドツールとして Gradle を採用するにあたり、DSL の選択が必要である。

- Spring Boot 4.0 プラグインは Gradle 9.x に対応済み
- 参考プロジェクト（case-study-cargo-tracker take-1）では Groovy DSL を採用している
- Kotlin DSL は Gradle 8.12.1 + Java 25 で Kotlin コンパイラが `JavaVersion.parse` に失敗する問題があった

## 決定

**Gradle 9.2.1 (Groovy DSL)** を採用���る。

### 変更箇所

- `apps/cargo-tracker/build.gradle`（Groovy DSL）
- `apps/cargo-tracker/settings.gradle`
- `apps/cargo-tracker/gradle/wrapper/gradle-wrapper.properties` で `gradle-9.2.1-bin.zip` を指定

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| Gradle Kotlin DSL | Gradle 8.x + Java 25 で Kotlin コンパイラの `JavaVersion.parse` が失敗。Gradle 9.x では解消されたが、参考プロジェクトとの一貫性を優先 |
| Maven | Gradle の柔軟なビルド設定と Spring Boot プラグインの統合度を優先 |

## 影響

### ポジティブ

- 参考プロジェクトとビルド設定の互換性が保たれる
- Groovy DSL の方がコード量が少なく可読性が高い

### ネガティブ

- Kotlin DSL に比べて IDE の補完サポートが弱い

## コンプライアンス

- `./gradlew --version` で Gradle 9.2.1 が表示されること
- `./gradlew build` が成功すること

## ��考

- 関連コミット: `cab0cb8`
