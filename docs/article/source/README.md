# 参照元ソース

各記事シリーズが引用している実装コードと一次資料です。

記事中の引用はすべてこのツリーの実ファイルから転記しています。

| ツリー | 引用元シリーズ |
| :--- | :--- |
| 10 言語の `apps/` | [モノリスアーキテクチャ実装比較](../monolith-architecture/index.md) / [関数型ドメインモデリング](../functional-domain-modeling/index.md) |
| [`java-2/`](java-2) | [実践 AI 駆動開発](../ai-driven-development/index.md) |

## 収録内容

各言語の `apps/` 配下（アプリケーション実装・テスト・ビルド設定・DB マイグレーション）のみを収録しています。

| 言語 | パス | ファイル数 | サイズ | フレームワーク |
| :--- | :--- | ---: | ---: | :--- |
| Java | [java/apps](java/apps) | 275 | 1.4M | Spring Boot 4.0 / MyBatis / Thymeleaf |
| C# | [csharp/apps](csharp/apps) | 343 | 1.6M | ASP.NET Core 10 / Dapper / Razor |
| F# | [fsharp/apps](fsharp/apps) | 127 | 1.1M | Giraffe / Donald / Giraffe.ViewEngine |
| Scala | [scala/apps](scala/apps) | 322 | 1.6M | Play Framework 3.0 / ScalikeJDBC / Twirl |
| Haskell | [haskell/apps](haskell/apps) | 378 | 2.1M | Servant + Warp / Lucid |
| Flix | [flix/apps](flix/apps) | 219 | 2.5M | JDK HttpServer / JDBC |
| Rust | [rust/apps](rust/apps) | 176 | 1.6M | axum + tokio / sqlx |
| Go | [go/apps](go/apps) | 311 | 1.6M | chi + net/http / html/template |
| Ruby | [ruby/apps](ruby/apps) | 372 | 1.6M | Rails 8 + packs / Active Record |
| TypeScript | [typescript/apps](typescript/apps) | 332 | 2.0M | NestJS 11 / Kysely / TSX |

合計 2,855 ファイル・約 17M です。

## java-2（実践 AI 駆動開発）

`java/take-6`（20 イテレーション・US01〜US36・v2.1.0 出荷済み）の実装と一次資料です。上表の Java（`java/apps`）とは**別の実装**であり、記事シリーズも別です。

| 種別 | パス | ファイル数 | サイズ | 内容 |
| :--- | :--- | ---: | ---: | :--- |
| 実装 | [java-2/apps](java-2/apps) | 850 | 5.6M | アプリケーション実装・テスト・ビルド設定・DB マイグレーション |
| 一次資料 | [java-2/docs](java-2/docs) | 144 | 2.7M | 計画・完了報告・ふりかえり・ADR・レビュー・ジャーナル・設計・戦略 |

**このシリーズだけ `docs/` を収録しています。** 主題が開発プロセスそのものであり、読者が実際のふりかえり・レビュー記録を突き合わせられることに価値があるためです。内訳は次の通りです。

| ディレクトリ | 内容 |
| :--- | :--- |
| `development/` | イテレーション計画 20・完了報告 20・ふりかえり 20・リリース計画／報告 |
| `journal/` | 開発ジャーナル（判断と学びの記録） |
| `review/` | マルチパースペクティブレビューの記録 |
| `adr/` | 設計判断の記録（26 件） |
| `design/` | 設計ドキュメント |
| `strategy/` | 開発戦略・インセプションデッキ |

参照元の `docs/article/`（本リポジトリと重複）・`manual/`・`reference/`・`template/`・`assets/` は収録していません。`requirements/` は本リポジトリの [要件](../../requirements/index.md) が US 採番の正典であるため、そちらを参照してください。

Skill の定義（`.claude/skills/`）も収録していません。**本リポジトリの `.claude/skills/` が同一の Skill 体系**です。

## 収録していないもの

参照元リポジトリには以下も含まれますが、本ツリーには収録していません。

| 除外対象 | 理由 |
| :--- | :--- |
| 各言語の `docs/`（計 7,286 md） | 設計・イテレーション計画・ふりかえり。記事側に要点を反映済み |
| `ops/` / `flake.nix` / `docker-compose.yml` | 環境構築・運用スクリプト |
| `.git`（計 1.1G） | 各言語が独立した Git リポジトリのため |
| `go/apps/cargo-tracker/server` | コンパイル済みバイナリ（9.9M）。参照元では追跡されているがソースではない |
| `.claude/` | AI エージェントのローカル設定・作業メモリ |

Git 管理外のビルド成果物（`target/`・`node_modules/`・`.stack-work/` 等）も、各リポジトリの追跡ファイルのみを移送しているため含まれません。

## 記事との対応

記事の各章が引用している主なファイルは次の通りです。

| 章 | 主な引用元（Java） |
| :--- | :--- |
| [第 2 章 IT1](../monolith-architecture/02-iteration-01.md) | `shipper/domain/model/` の集約・値オブジェクト、`CargoBookingCommandService` |
| [第 3 章 IT2](../monolith-architecture/03-iteration-02.md) | `booking/domain/model/aggregates/Cargo.java`、`ShipperExistenceChecker` |
| [第 4 章 IT3](../monolith-architecture/04-iteration-03.md) | `estimation/domain/model/`、`StubRouteCandidateProvider` |
| [第 5 章 IT4](../monolith-architecture/05-iteration-04.md) | `routing/domain/model/Voyage.java`、`VoyageRouteCandidateProvider`、`architecture/HexagonalArchitectureTest.java` |
| [第 6 章 IT5](../monolith-architecture/06-iteration-05.md) | `booking/domain/model/valueobjects/CargoItinerary.java`・`Leg.java` |
| [第 7 章 IT6](../monolith-architecture/07-iteration-06.md) | `billing/domain/model/` の `Invoice`・`DiscountPolicy` |
| [第 8 章 IT7](../monolith-architecture/08-iteration-07.md) | `tracking/domain/model/` の `TrackingRecord`・`TrackingNumber` |
| [第 9 章 IT8](../monolith-architecture/09-iteration-08.md) | `tracking/interfaces/web/PublicTrackingController.java`、`TrackingDetailDto` |
| [第 10 章 IT9](../monolith-architecture/10-iteration-09.md) | `tracking/domain/model/valueobjects/ExceptionType.java`、`TrackingCommandService` |
| [第 11 章 IT10](../monolith-architecture/11-iteration-10.md) | `billing/domain/model/services/FreightCalculationService.java` |

Java 実装のパッケージルートは `java/apps/cargo-tracker/src/main/java/com/example/cargotracker/` です。

## MkDocs 上の扱い

このディレクトリは `mkdocs.yml` の `exclude_docs` で除外しており、サイトのページとしてはビルドされません。リポジトリ上のソースとして参照してください。
