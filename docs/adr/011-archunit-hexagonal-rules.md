# ADR-011: ArchUnit でヘキサゴナルアーキテクチャの依存関係ルールを自動検証する

ArchUnit を使用して、パッケージ間の依存関係ルールを CI で自動検証し、アーキテクチャの逸脱を防止する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

ADR-010 で Practical DDD のパッケージ構成を採用した。しかし、パッケージ構成を定義しただけでは、開発中にルール逸脱が発生してもコードレビューまで検知できない。

- ドメイン層がインフラ層に依存するコードが混入すると、ヘキサゴナルアーキテクチャが崩壊する
- コンテキスト間で直接クラスを参照すると、境界付けられたコンテキストの自律性が失われる
- 手動レビューだけではルール逸脱を完全に防止できない

## 決定

**ArchUnit 1.4.1 を使用し、以下の依存関係ルールをテストとして自動検証する。**

### 検証ルール

| # | ルール | 検証内容 |
|---|--------|---------|
| 1 | ドメイン層の独立性 | `domain.model` パッケージが `infrastructure`・`interfaces` パッケージに依存しないこと |
| 2 | ドメイン層の技術非依存 | `domain.model` パッケージに `@Component`・`@Service`・`@Repository`・`@Controller` が含まれないこと |
| 3 | アプリケーション層の独立性 | `application` パッケージが `interfaces` パッケージに依存しないこと |
| 4 | コンテキスト間の分離 | 異なる Bounded Context（`booking`・`shipper`・`routing` 等）が互いのクラスを直接参照しないこと（`shareddomain` 経由のみ） |

### テストクラス配置

```
src/test/java/com/example/cargotracker/
└── architecture/
    └── HexagonalArchitectureTest.java
```

### テスト実装例

```java
@AnalyzeClasses(packages = "com.example.cargotracker")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
        noClasses().that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_interfaces =
        noClasses().that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    @ArchTest
    static final ArchRule domain_should_not_use_spring_annotations =
        noClasses().that().resideInAPackage("..domain.model..")
            .should().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Repository");

    @ArchTest
    static final ArchRule application_should_not_depend_on_interfaces =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..interfaces..");
}
```

### 変更箇所

- `build.gradle` に `com.tngtech.archunit:archunit-junit5:1.4.1` を追加済み
- `src/test/java/.../architecture/HexagonalArchitectureTest.java` を IT1 で作成

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| 手動コードレビューのみ | 見落としリスクが高く、スケールしない |
| jdepend | ArchUnit の方が API が現代的で JUnit 5 と統合しやすい |
| Spring Modulith | ヘキサゴナルアーキテクチャの細かいルール検証には ArchUnit の方が柔軟 |

## 影響

### ポジティブ

- アーキテクチャ逸脱が CI で自動検知され、コードレビュー前にフィードバックが得られる
- ルールがコードとして管理されるため、チーム全員が同じ基準を共有できる
- テスト実行のたびに検証されるため、継続的にアーキテクチャ品質が維持される

### ネガティブ

- ArchUnit テストの追加により全テスト実行時間がわずかに増加する
- ルールの更新・追加にはアーキテクチャの理解が必要

## コンプライアンス

- `./gradlew test --tests "*.architecture.*"` で ArchUnit テストが全てパスすること
- CI ワークフロー（`.github/workflows/ci.yml`）で自動実行されること
- 新しいコンテキストを追加した際に、コンテキスト間分離ルールを更新すること

## 備考

- 関連 ADR: ADR-010（パッケージ構成）
- 参考: ArchUnit User Guide (https://www.archunit.org/userguide/html/000_Index.html)
