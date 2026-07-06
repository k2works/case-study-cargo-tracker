---
title: テスト戦略 - 国際貨物輸送管理システム（Rust 版）
description: DDD・ヘキサゴナル・CQRS アーキテクチャに基づく Rust 版テスト戦略。ピラミッド型テスト形状とテストレベルの定義、カバレッジ目標、cargo test / mockall / testcontainers-rs / wiremock / Playwright のツール選定を記録する。
published: true
date: 2026-07-06T00:00:00.000Z
tags: design, test-strategy, tdd, hexagonal, cqrs, rust
---

# テスト戦略 - 国際貨物輸送管理システム（Rust 版）

## 1. 概要

### 1.1 目的

本ドキュメントは、国際貨物輸送管理システム（Rust 実装）におけるテスト戦略を定義する。テスト戦略を事前に策定し、以下の問いに常に回答できる状態を維持することを目的とする。

- 「この機能はどのテストレベルで保証されているか」
- 「何をどこまでテストすべきか」
- 「テストが失敗したとき、どこを修正すべきか」

### 1.2 基本方針

- **TDD（テスト駆動開発）を全開発プロセスで適用する**: レッド → グリーン → リファクタリングのサイクルを厳守する
- **テストをアーキテクチャに対応させる**: ヘキサゴナルアーキテクチャの境界（ポート trait）を活かし、テスト可能性を設計段階で確保する
- **コンパイラを最初のテストとして扱う**: Rust の型システム・所有権・網羅的 `match` により、状態遷移漏れや null 参照はコンパイル時に排除する。テストは型で表現できない振る舞いの検証に集中する
- **テストの重複を排除する**: 各テストレベルの責務を明確に分離し、同一ロジックを複数レベルで重複検証しない
- **テストを実行可能なドキュメントとして扱う**: テストコードがシステムの振る舞いを説明する

### 1.3 アーキテクチャとテスト戦略の対応関係

```plantuml
@startuml
!theme plain

package "ユニットテスト対象" {
  package "domain クレート" {
    [Cargo 集約]
    [Voyage 集約]
    [HandlingActivity 集約]
    [Invoice 集約]
    [TrackingActivity 集約]
    [値オブジェクト（Location 等）]
  }
  package "application クレート" {
    [BookingService]
    [RoutingService]
    [TrackingQueryService]
    [BillingService]
  }
}

package "統合テスト対象" {
  package "infrastructure クレート（入力側アダプター）" {
    [booking ハンドラ (axum)]
    [tracking ハンドラ (axum)]
  }
  package "infrastructure クレート（出力側アダプター）" {
    [CargoRepository 実装 (sqlx)]
    [ExternalRoutingAdapter (reqwest)]
  }
}

package "E2E テスト対象" {
  [Playwright シナリオ]
}

[Cargo 集約] --> [BookingService]
[BookingService] --> [booking ハンドラ (axum)]
[booking ハンドラ (axum)] --> [Playwright シナリオ]

note right of [値オブジェクト（Location 等）]
  ヘキサゴナルアーキテクチャ境界ごとに
  テストレベルを分離する
end note

@enduml
```

ヘキサゴナルアーキテクチャの各層は以下のテストレベルに対応する。

| アーキテクチャ層 | テストレベル | 理由 |
|---|---|---|
| ドメイン層（集約・値オブジェクト・ドメインサービス） | ユニットテスト（`#[cfg(test)]`） | 外部依存ゼロ。純粋なビジネスロジック |
| アプリケーション層（ユースケースサービス） | ユニットテスト（ポート trait を mockall でモック） | ポートへの委譲とオーケストレーションを検証 |
| 入力側アダプター（axum ハンドラ） | 統合テスト（`tower::ServiceExt::oneshot`） | HTTP マッピングとバリデーションを検証 |
| 出力側アダプター（Repository） | 統合テスト（testcontainers-rs + sqlx） | SQL クエリの正確性を実 DB で検証 |
| 外部 ACL ポート（5 件） | 統合テスト（wiremock） | 外部システムとの契約を検証 |
| ユーザーシナリオ全体 | E2E テスト（Playwright） | クリティカルパスの品質保証 |

---

## 2. テスト形状の選択

### 2.1 採用形状: ピラミッド型

```plantuml
@startditaa
        /------------------\
       /   E2E テスト (5%)  \
      /   Playwright 1.44+  \
     /----------------------\
    /    統合テスト (25%)     \
   / testcontainers-rs + oneshot\
  /  + wiremock + cargo-deny    \
 /----------------------------\
/       ユニットテスト (70%)    \
/ cargo test + mockall          \
/ + pretty_assertions + proptest \
/--------------------------------\
@endditaa
```

**採用理由**:

- **ドメイン層が厚い**: DDD を採用しており、Cargo・Voyage・HandlingActivity・Invoice の各集約にビジネスロジックが集中する。BookingStatus の 8 値遷移、荷役妥当性検証（MISROUTED 判定）、法人割引計算など、外部依存なしでテスト可能なロジックが多い
- **ヘキサゴナルアーキテクチャによる高いテスト可能性**: ドメイン層とインフラ層の境界がポート trait で分離されており、mockall によるモックの差し替えが容易。ユニットテストが書きやすい設計になっている
- **CQRS による読み取りモデルの分離**: Tracking コンテキストの読み取りクエリはドメインロジックを持たず、統合テストで Repository を直接検証するだけで十分
- **コスト効率**: ユニットテストはネイティブバイナリで実行が高速（< 30 秒）でメンテナンスコストが低い。E2E テストはフレイキーになりやすく、最小限にとどめることで CI の安定性を維持する

### 2.2 採用しない形状と理由

| 形状 | 採用しない理由 |
|---|---|
| **ダイヤモンド型**（統合テスト重視） | 本システムは単一モノリス（ヘキサゴナル、cargo workspace 構成）で構成されており、マイクロサービス間の契約検証ニーズがない。統合テストを主軸にするとテスト実行時間が増大し、TDD サイクルが遅くなる |
| **逆ピラミッド型**（E2E 重視） | Playwright テストはヘッドレスブラウザを起動するためフレイキーになりやすく、htmx の 30 秒ポーリングを含む動的 UI はテストの安定性確保が困難。E2E を主軸にするとフィードバックループが 15 分以上になる |

---

## 3. テストレベルの定義

### 3.1 ユニットテスト（Unit Test）

#### 責務・検証対象

- **ドメイン層**: 集約の状態遷移・不変条件・ビジネスルール、値オブジェクトの等価性・バリデーション、ドメインサービスのロジック
- **アプリケーション層**: ユースケースサービスのオーケストレーション（ポート trait は mockall でモック）

#### カバレッジ目標

| 対象 | 行カバレッジ | 分岐カバレッジ |
|---|---|---|
| ドメイン層 | **85% 以上** | **80% 以上** |
| アプリケーション層 | **80% 以上** | **75% 以上** |

目標値は初期仮説であり、実測によるキャリブレーション方針は「6.1 レイヤー別カバレッジ目標」の注記を参照。

#### 使用ツール

- **cargo test**: テストフレームワーク（ユニットテストは各モジュール内の `#[cfg(test)]` モジュールに配置）
- **mockall 0.13+**: ポート trait のモック（`#[automock]` / `mock!` マクロ）
- **pretty_assertions**: 標準 `assert!` / `assert_eq!` に差分表示を追加した流暢なアサーション
- **proptest**（追加提案）: 値オブジェクトの不変条件をプロパティベースで検証（UN/LOCODE のバリデーション、Money の演算則など）

#### 実行タイミング

- **ローカル**: すべてのコミット時（目標 **30 秒以内**）
- **PR**: 自動実行（コミットプッシュ時）
- **CI**: GitHub Actions の `unit-test` ジョブ

#### 除外対象

- インフラ層（sqlx クエリ、reqwest クライアント）— 統合テストで担保する
- DTO / 単純な `struct`（derive のみ）— データ保持のみでロジックがない
- axum の Router 構築・DI 配線 — 統合テストで担保する。ユニットテストでアプリ全体を起動**しない**

#### 実装例: Cargo 集約の BookingStatus 遷移テスト

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use pretty_assertions::assert_eq;

    #[test]
    fn 予約が確定できる() {
        // Given: ルートが割り当て済みの貨物
        let mut cargo = CargoFixture::with_route_assigned();

        // When: 予約を確定する
        cargo.confirm_booking().expect("確定できるはず");

        // Then: ステータスが Confirmed に遷移する
        assert_eq!(cargo.booking_status(), BookingStatus::Confirmed);
    }

    #[test]
    fn ルート未割り当て状態で予約確定しようとするとエラーが返る() {
        // Given: ルートが未割り当ての貨物
        let mut cargo = CargoFixture::preliminary();

        // When: 予約を確定する
        let result = cargo.confirm_booking();

        // Then: 不変条件違反でエラーが返る
        assert!(matches!(
            result,
            Err(BookingDomainError::RouteNotAssigned { .. })
        ));
    }

    #[test]
    fn 危険物の取扱不可港にルートを割り当てるとエラーが返る() {
        // Given: 危険物フラグが立った貨物と危険物取扱不可の港を経由するルート
        let mut cargo = CargoFixture::hazardous();
        let prohibited_route = RouteFixture::via_hazardous_prohibited_port();

        // When & Then: ドメインルール違反でエラーが返る
        let result = cargo.assign_route(prohibited_route);
        assert!(matches!(
            result,
            Err(BookingDomainError::HazardousCargoRouting { .. })
        ));
    }

    #[test]
    fn 終端状態からの遷移は許可されない() {
        // Given: 終端ステータスの貨物（rstest のパラメータ化の代わりにループで網羅）
        for terminal in [
            BookingStatus::Settled,
            BookingStatus::Cancelled,
        ] {
            let mut cargo = CargoFixture::with_status(terminal);

            // When & Then: ステータス遷移が拒否される
            let result = cargo.confirm_booking();
            assert!(
                matches!(
                    result,
                    Err(BookingDomainError::InvalidStatusTransition { .. })
                ),
                "{terminal:?} からの遷移は拒否されるべき"
            );
        }
    }
}
```

#### 実装例: proptest による値オブジェクトの不変条件検証（追加提案）

```rust
#[cfg(test)]
mod prop_tests {
    use super::*;
    use proptest::prelude::*;

    proptest! {
        /// UN/LOCODE は大文字英字 5 文字のみ受理される
        #[test]
        fn 有効なUNLOCODEは常にパースに成功する(code in "[A-Z]{5}") {
            prop_assert!(UnLocode::new(&code).is_ok());
        }

        /// 5 文字以外・小文字・数字を含む文字列は必ず拒否される
        #[test]
        fn 不正な文字列は必ず拒否される(code in "[a-z0-9]{1,10}") {
            prop_assert!(UnLocode::new(&code).is_err());
        }

        /// Money の加算は結合的である（金額計算の演算則）
        /// Money は rust_decimal::Decimal + 通貨コードを保持し、
        /// add は通貨不一致を検出するため Result<Money, DomainError> を返す
        #[test]
        fn 金額の加算は結合的(a in 0i64..1_000_000, b in 0i64..1_000_000, c in 0i64..1_000_000) {
            let (a, b, c) = (
                Money::jpy(Decimal::from(a)),
                Money::jpy(Decimal::from(b)),
                Money::jpy(Decimal::from(c)),
            );
            // 同一通貨（JPY）同士のため add は必ず Ok を返す。
            // prop_assert_eq! に渡す前に unwrap で Money を取り出す
            let left = a.add(&b).unwrap().add(&c).unwrap();
            let right = a.add(&b.add(&c).unwrap()).unwrap();
            prop_assert_eq!(left, right);
        }
    }
}
```

#### 実装例: mockall によるアプリケーション層テスト

```rust
// application クレート内のポート trait 定義
#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait CargoRepositoryPort: Send + Sync {
    async fn save(&self, cargo: &Cargo) -> Result<(), RepositoryError>;
    async fn find_by_tracking_id(
        &self,
        tracking_id: &TrackingId,
    ) -> Result<Option<Cargo>, RepositoryError>;
}

#[cfg(test)]
mod tests {
    use super::*;
    use pretty_assertions::assert_eq;

    #[tokio::test]
    async fn 予約登録でリポジトリに保存されて追跡番号が返る() {
        // Given: save が 1 回呼ばれることを期待するモック
        let mut repo = MockCargoRepositoryPort::new();
        repo.expect_save().times(1).returning(|_| Ok(()));
        let service = BookingService::new(Arc::new(repo));

        // When: 新規予約を登録する
        let tracking_id = service
            .book_new_cargo(BookNewCargoCommand {
                origin: UnLocode::new("JPTYO").unwrap(),
                destination: UnLocode::new("DEHAM").unwrap(),
                arrival_deadline: date!(2026 - 06 - 30),
            })
            .await
            .expect("予約登録に成功するはず");

        // Then: 追跡番号が発行される
        assert!(!tracking_id.as_str().is_empty());
    }
}
```

---

### 3.2 統合テスト（Integration Test）

#### 責務・検証対象

- **Repository（sqlx）**: SQL クエリの正確性、トランザクション、楽観的ロック
- **HTTP ハンドラ（axum + `tower::ServiceExt::oneshot`）**: HTTP リクエスト/レスポンスのマッピング、バリデーション、エラーハンドリング
- **外部 ACL ポート（wiremock）**: 外部システムとの契約遵守、タイムアウト・フォールバック

統合テストは各クレートの `tests/` ディレクトリに配置する。

#### カバレッジ目標

| 対象 | 行カバレッジ |
|---|---|
| Repository（インフラ層） | **75% 以上** |
| ハンドラ層 | **70% 以上** |

#### 使用ツール

- **cargo test（tests/ ディレクトリ）**: 統合テストフレームワーク
- **testcontainers-rs**: 実 PostgreSQL 16 コンテナを自動起動（H2 のようなインメモリ代替 DB は使わない。本番と同一エンジンで検証する）
- **sqlx**: コンパイル時検証付き SQL クエリ + マイグレーション適用
- **axum + tower::ServiceExt::oneshot**: HTTP 層の結合テスト（サーバーソケットを開かずに Router へ直接リクエストを送る。MockMvc の代替）
- **wiremock クレート**: 外部 ACL ポートのスタブ（5 件すべてを対象）

#### 実行タイミング

- **PR 時**: GitHub Actions の `integration-test` ジョブ（目標 **5 分以内**）
- **ローカル**: Docker が起動している環境で任意実行

#### 実装例: CargoRepository の保存・検索テスト（testcontainers-rs）

```rust
// infrastructure/tests/cargo_repository_test.rs
use sqlx::PgPool;
use testcontainers_modules::{postgres::Postgres, testcontainers::runners::AsyncRunner};

async fn setup_pool() -> (PgPool, impl Drop) {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("PostgreSQL コンテナ起動");
    let url = format!(
        "postgres://postgres:postgres@127.0.0.1:{}/postgres",
        container.get_host_port_ipv4(5432).await.unwrap()
    );
    let pool = PgPool::connect(&url).await.unwrap();
    sqlx::migrate!("./migrations").run(&pool).await.unwrap();
    (pool, container)
}

#[tokio::test]
async fn 貨物を保存して追跡番号で検索できる() {
    // Given: 実 PostgreSQL 16 と新規貨物エンティティ
    let (pool, _container) = setup_pool().await;
    let repo = PgCargoRepository::new(pool);
    let cargo = CargoFixture::new_booking(
        TrackingId::new("CARGO-001").unwrap(),
        UnLocode::new("JPTYO").unwrap(),
        UnLocode::new("DEHAM").unwrap(),
    );

    // When: 保存して検索する
    repo.save(&cargo).await.unwrap();
    let found = repo
        .find_by_tracking_id(&TrackingId::new("CARGO-001").unwrap())
        .await
        .unwrap();

    // Then: 保存したエンティティと一致する
    let found = found.expect("貨物が見つかるはず");
    assert_eq!(found.origin(), &UnLocode::new("JPTYO").unwrap());
    assert_eq!(found.destination(), &UnLocode::new("DEHAM").unwrap());
}

#[tokio::test]
async fn 存在しない追跡番号で検索するとNoneを返す() {
    // Given & When
    let (pool, _container) = setup_pool().await;
    let repo = PgCargoRepository::new(pool);
    let result = repo
        .find_by_tracking_id(&TrackingId::new("NONEXISTENT").unwrap())
        .await
        .unwrap();

    // Then
    assert!(result.is_none());
}
```

#### 実装例: booking ハンドラの oneshot テスト

```rust
// infrastructure/tests/booking_handler_test.rs
use axum::{
    body::Body,
    http::{Request, StatusCode},
};
use tower::ServiceExt; // oneshot

#[tokio::test]
async fn 貨物予約登録APIが201を返す() {
    // Given: 予約登録に成功するモックサービスを注入した Router
    let mut service = MockBookingServicePort::new();
    service
        .expect_book_new_cargo()
        .returning(|_| Ok(TrackingId::new("CARGO-001").unwrap()));
    let app = booking_router(Arc::new(service));

    let request = Request::post("/api/bookings")
        .header("content-type", "application/json")
        .body(Body::from(
            r#"{
              "originUnLocode": "JPTYO",
              "destinationUnLocode": "DEHAM",
              "arrivalDeadline": "2026-06-30"
            }"#,
        ))
        .unwrap();

    // When
    let response = app.oneshot(request).await.unwrap();

    // Then
    assert_eq!(response.status(), StatusCode::CREATED);
    let body: serde_json::Value = read_json_body(response).await;
    assert_eq!(body["trackingId"], "CARGO-001");
}

#[tokio::test]
async fn 出発地コードが不正な場合は400を返す() {
    // Given: 不正な UN/LOCODE を含むリクエスト
    let app = booking_router(Arc::new(MockBookingServicePort::new()));
    let request = Request::post("/api/bookings")
        .header("content-type", "application/json")
        .body(Body::from(
            r#"{
              "originUnLocode": "INVALID",
              "destinationUnLocode": "DEHAM",
              "arrivalDeadline": "2026-06-30"
            }"#,
        ))
        .unwrap();

    // When
    let response = app.oneshot(request).await.unwrap();

    // Then
    assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    let body: serde_json::Value = read_json_body(response).await;
    assert_eq!(body["errors"][0]["field"], "originUnLocode");
}
```

#### wiremock 契約テストの概要

各 ACL ポートに対して wiremock スタブを定義する。詳細は [セクション 4](#4-wiremock-契約テストシナリオacl-ポート別) を参照。

---

### 3.3 アーキテクチャ検証（Architecture Enforcement）

#### 責務・検証対象

ヘキサゴナルアーキテクチャの依存関係ルールを検証する。Java 版の ArchUnit に相当する仕組みは、Rust では **cargo workspace のクレート分割によるコンパイラ強制**で実現する。`Cargo.toml` の依存宣言そのものが構造ルールであり、違反はテスト失敗ではなく**コンパイルエラー**として即時検出される。

#### 使用ツール

- **cargo workspace（クレート分割）**: 依存方向の物理的強制
- **cargo-deny**: 依存ポリシーの補助検証（ライセンス・重複・禁止クレートの検出）

#### 検証ルール 4 件と実現方法

| # | ルール（Java 版 ArchUnit 相当） | Rust での強制手段 |
|---|---|---|
| 1 | ドメイン層はインフラ層に依存しない | `domain` クレートの `Cargo.toml` に `infrastructure` を記載しない。逆方向の依存はコンパイル不能 |
| 2 | ドメイン層はフレームワークに依存しない | `domain` クレートの依存に `axum`・`sqlx`・`tokio` を含めない（`serde` 等の最小限のみ許可）。cargo-deny の `bans` で禁止リストを機械検証 |
| 3 | アプリケーション層はインフラ層を直接参照しない（ポート経由のみ） | `application` クレートはポート trait を自クレート内に定義し、`infrastructure` に依存しない。DI（実装の注入）は最上位の `main` クレートのみが行う |
| 4 | Bounded Context 間でモジュールを直接参照しない | コンテキストごとにモジュール（または将来クレート）を分離し、共有カーネルは `shared` クレートに限定。`pub(crate)` / 非公開モジュールで可視性を制限 |

#### workspace 構成（Cargo.toml が構造ルール）

```toml
# Cargo.toml (workspace root)
[workspace]
members = ["crates/domain", "crates/application", "crates/infrastructure", "crates/shared", "crates/app"]

# crates/domain/Cargo.toml — axum / sqlx / tokio を含めないことがルール
[dependencies]
shared = { path = "../shared" }
thiserror = "2"
time = "0.3"

# crates/application/Cargo.toml — infrastructure に依存しないことがルール
[dependencies]
domain = { path = "../domain" }
shared = { path = "../shared" }
async-trait = "0.1"
mockall = { version = "0.13", optional = true }

# crates/infrastructure/Cargo.toml — domain / application に依存する（逆は不可）
[dependencies]
domain = { path = "../domain" }
application = { path = "../application" }
axum = "0.8"
sqlx = { version = "0.8", features = ["postgres", "runtime-tokio"] }
```

#### cargo-deny による補助検証

```toml
# deny.toml — domain クレートへのフレームワーク混入を CI で検出
[bans]
multiple-versions = "warn"

[[bans.deny]]
name = "axum"
wrappers = ["infrastructure", "app"]  # infrastructure / app 以外での使用を禁止

[[bans.deny]]
name = "sqlx"
wrappers = ["infrastructure", "app"]
```

#### 実行タイミング

- **常時**: `cargo build` / `cargo test` 実行時にコンパイラが強制（追加コストゼロ）
- **PR 時**: GitHub Actions で `cargo deny check` を実行

---

### 3.4 E2E テスト（End-to-End Test）

#### 責務・検証対象

クリティカルなユーザーシナリオをブラウザレベルで検証する。ドメインロジックの再検証は行わず、ユーザー体験の観点からシステム全体が協調動作することを確認する。

**優先シナリオ（US08・US10・US13）**:

| シナリオ | 理由 |
|---|---|
| US08: 予約を確定する | 予約フローの最終ステップ。複数コンテキストが連携する |
| US10: 荷役作業を記録する | 最も頻繁に実行される運用操作 |
| US13: 追跡情報を照会する | 顧客向け重要機能。htmx ポーリングを含む |

#### カバレッジ目標

- 優先度「高」のユーザーシナリオ（US01〜US15）の **80% カバー**

#### 使用ツール

- **Playwright 1.44+**: ブラウザ自動化（TypeScript）
- **htmx 対応**: `waitForSelector` によるポーリング更新の待機

#### 実行タイミング

- **main ブランチマージ後**: GitHub Actions の `e2e-test` ジョブ（目標 **15 分以内**）
- **リリース前**: 全 E2E シナリオを実行

#### htmx 30 秒ポーリングへの対応

htmx の `hx-trigger="every 30s"` による自動更新を Playwright でテストするには、`waitForFunction` でポーリング後の DOM 更新を待機する。

```typescript
// htmx ポーリング完了を待機するユーティリティ
async function waitForHtmxUpdate(page: Page, selector: string, timeout = 35000) {
  // htmx が更新した要素に hx-request 属性が付与されるため、
  // その変化を監視してポーリング完了を検出する
  await page.waitForFunction(
    (sel) => {
      const el = document.querySelector(sel);
      return el && !el.hasAttribute('hx-request');
    },
    selector,
    { timeout }
  );
}
```

#### 実装例: US13 追跡情報照会の Playwright テスト（TypeScript）

```typescript
import { test, expect, Page } from '@playwright/test';

test.describe('US13: 追跡情報を照会する', () => {
  let page: Page;

  test.beforeEach(async ({ browser }) => {
    page = await browser.newPage();
  });

  test('追跡番号で貨物の現在状態を照会できる', async () => {
    // Given: 荷役作業が記録済みの貨物が存在する
    await page.goto('/tracking');

    // When: 追跡番号を入力して検索する
    await page.fill('[data-testid="tracking-id-input"]', 'CARGO-001');
    await page.click('[data-testid="search-button"]');

    // Then: 追跡情報が表示される
    await expect(page.locator('[data-testid="transport-status"]'))
      .toHaveText('ONBOARD_CARRIER', { timeout: 10000 });
    await expect(page.locator('[data-testid="current-location"]'))
      .toContainText('東京港');
  });

  test('htmx ポーリングで追跡情報が自動更新される', async () => {
    // Given: 追跡ページを表示している
    await page.goto('/tracking/CARGO-001');
    const initialStatus = await page
      .locator('[data-testid="transport-status"]')
      .textContent();

    // When: バックエンドで荷役イベントが発生し、30 秒後にポーリングが更新される
    // （テスト環境ではポーリング間隔を 5 秒に短縮）
    await waitForHtmxUpdate(page, '[data-testid="tracking-panel"]', 10000);

    // Then: ページを再読み込みせずに最新状態が反映される
    const updatedStatus = await page
      .locator('[data-testid="transport-status"]')
      .textContent();
    expect(updatedStatus).not.toBe(initialStatus);
  });

  test('存在しない追跡番号を入力するとエラーメッセージが表示される', async () => {
    // Given
    await page.goto('/tracking');

    // When
    await page.fill('[data-testid="tracking-id-input"]', 'NONEXISTENT-999');
    await page.click('[data-testid="search-button"]');

    // Then
    await expect(page.locator('[data-testid="error-message"]'))
      .toContainText('追跡番号が見つかりません');
  });
});

async function waitForHtmxUpdate(page: Page, selector: string, timeout = 35000) {
  await page.waitForFunction(
    (sel) => {
      const el = document.querySelector(sel);
      return el && !el.hasAttribute('hx-request');
    },
    selector,
    { timeout }
  );
}
```

---

## 4. wiremock 契約テストシナリオ（ACL ポート別）

各外部 ACL ポートに対して正常・異常シナリオを定義し、wiremock クレートでスタブ化する。

### 4.1 シナリオ一覧

| ポート | 正常シナリオ | 異常シナリオ |
|---|---|---|
| ExternalRoutingServicePort | ルート検索 → 3 候補返却 | 接続タイムアウト → 過去実績データにフォールバック |
| CustomsClearancePort | 通関申請 → CLEARED | HELD ステータス → 例外イベント発行 |
| PaymentGatewayPort | 支払い処理 → CONFIRMED | 決済失敗 → OVERDUE 状態遷移 |
| PortManagementPort | 港湾入港通知 → 受理 | 港湾満杯 → 代替港提案 |
| NotificationPort | メール通知送信 → 202 Accepted | 通知失敗 → ログ記録（非クリティカル） |

### 4.2 wiremock 実装例

#### ExternalRoutingServicePort: ルート検索（正常・タイムアウト）

```rust
// infrastructure/tests/external_routing_adapter_test.rs
use wiremock::{
    matchers::{body_json_string_contains, method, path},
    Mock, MockServer, ResponseTemplate,
};

#[tokio::test]
async fn ルート検索で3候補が返却される() {
    // Given: wiremock スタブ定義（3 候補を返す）
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/routes/search"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(
            r#"{
              "routes": [
                {"id": "R001", "legs": [{"voyageNumber": "V001"}], "transitTime": 14},
                {"id": "R002", "legs": [{"voyageNumber": "V002"}], "transitTime": 18},
                {"id": "R003", "legs": [{"voyageNumber": "V003"}], "transitTime": 21}
              ]
            }"#,
            "application/json",
        ))
        .mount(&server)
        .await;
    let adapter = ExternalRoutingAdapter::new(server.uri(), Duration::from_secs(5));

    // When: ルート検索を実行する
    let request = RouteSearchRequest::new(
        UnLocode::new("JPTYO").unwrap(),
        UnLocode::new("DEHAM").unwrap(),
        date!(2026 - 06 - 30),
    );
    let routes = adapter.search_routes(&request).await.unwrap();

    // Then: 3 候補が返却される
    assert_eq!(routes.len(), 3);
    assert_eq!(routes[0].transit_days(), 14);
}

#[tokio::test]
async fn 接続タイムアウト時に過去実績データにフォールバックする() {
    // Given: タイムアウトを発生させるスタブ（6 秒遅延、閾値 5 秒を超過）
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/routes/search"))
        .respond_with(ResponseTemplate::new(200).set_delay(Duration::from_secs(6)))
        .mount(&server)
        .await;
    let adapter = ExternalRoutingAdapter::new(server.uri(), Duration::from_secs(5));

    // When: ルート検索を実行する
    let request = RouteSearchRequest::new(
        UnLocode::new("JPTYO").unwrap(),
        UnLocode::new("DEHAM").unwrap(),
        date!(2026 - 06 - 30),
    );
    let routes = adapter.search_routes(&request).await.unwrap();

    // Then: 過去実績データからフォールバック候補が返却される
    assert!(!routes.is_empty());
    assert!(routes.iter().all(|route| route.is_fallback()));
}
```

#### CustomsClearancePort: 通関申請（CLEARED・HELD）

```rust
#[tokio::test]
async fn 通関申請が承認されてCLEAREDステータスを返す() {
    // Given
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/customs/clearance"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(
            r#"{"status": "CLEARED", "clearanceId": "CUS-001"}"#,
            "application/json",
        ))
        .mount(&server)
        .await;
    let adapter = CustomsClearanceAdapter::new(server.uri());

    // When
    let result = adapter
        .submit_clearance(&ClearanceRequest::new(TrackingId::new("CARGO-001").unwrap()))
        .await
        .unwrap();

    // Then
    assert_eq!(result.status(), ClearanceStatus::Cleared);
}

#[tokio::test]
async fn 通関保留HELDステータス受信時に例外イベントが発行される() {
    // Given
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/customs/clearance"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(
            r#"{"status": "HELD", "reason": "書類不備", "holdId": "HOLD-001"}"#,
            "application/json",
        ))
        .mount(&server)
        .await;
    let adapter = CustomsClearanceAdapter::new(server.uri());

    // When
    let result = adapter
        .submit_clearance(&ClearanceRequest::new(TrackingId::new("CARGO-002").unwrap()))
        .await
        .unwrap();

    // Then: HELD ステータスが返却され、例外イベントが発行可能な状態になる
    assert_eq!(result.status(), ClearanceStatus::Held);
    assert_eq!(result.hold_reason(), Some("書類不備"));
}
```

#### PaymentGatewayPort: 支払い処理（CONFIRMED・失敗）

```rust
#[tokio::test]
async fn 支払い処理が成功してCONFIRMEDを返す() {
    // Given
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/payments"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(
            r#"{"status": "CONFIRMED", "transactionId": "TXN-001"}"#,
            "application/json",
        ))
        .mount(&server)
        .await;
    let adapter = PaymentGatewayAdapter::new(server.uri());

    // When
    let result = adapter
        .process_payment(&PaymentRequest::new(
            InvoiceId::new("INV-001").unwrap(),
            Money::jpy(dec!(150_000)),
        ))
        .await
        .unwrap();

    // Then
    assert_eq!(result.status(), PaymentStatus::Confirmed);
}

#[tokio::test]
async fn 決済失敗時にOVERDUE状態への遷移情報が返却される() {
    // Given: 決済失敗レスポンス
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/payments"))
        .respond_with(ResponseTemplate::new(402).set_body_raw(
            r#"{"status": "FAILED", "errorCode": "INSUFFICIENT_FUNDS"}"#,
            "application/json",
        ))
        .mount(&server)
        .await;
    let adapter = PaymentGatewayAdapter::new(server.uri());

    // When
    let result = adapter
        .process_payment(&PaymentRequest::new(
            InvoiceId::new("INV-002").unwrap(),
            Money::jpy(dec!(500_000)),
        ))
        .await
        .unwrap();

    // Then: 失敗情報が返却される（OVERDUE 遷移はドメイン層が担当）
    assert_eq!(result.status(), PaymentStatus::Failed);
    assert_eq!(result.error_code(), Some("INSUFFICIENT_FUNDS"));
}
```

#### PortManagementPort: 港湾入港通知（受理・代替港提案）

```rust
#[tokio::test]
async fn 港湾入港通知が受理される() {
    // Given
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/ports/arrival"))
        .respond_with(ResponseTemplate::new(202).set_body_raw(
            r#"{"accepted": true, "berthId": "BERTH-A1"}"#,
            "application/json",
        ))
        .mount(&server)
        .await;
    let adapter = PortManagementAdapter::new(server.uri());

    // When
    let result = adapter
        .notify_arrival(&ArrivalNotification::new(
            UnLocode::new("JPTYO").unwrap(),
            VoyageNumber::new("V001").unwrap(),
        ))
        .await
        .unwrap();

    // Then
    assert!(result.is_accepted());
    assert_eq!(result.berth_id(), Some("BERTH-A1"));
}

#[tokio::test]
async fn 港湾満杯時に代替港が提案される() {
    // Given
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/ports/arrival"))
        .respond_with(ResponseTemplate::new(409).set_body_raw(
            r#"{
              "accepted": false,
              "reason": "PORT_FULL",
              "alternativePorts": ["JPYOK", "JPKOB"]
            }"#,
            "application/json",
        ))
        .mount(&server)
        .await;
    let adapter = PortManagementAdapter::new(server.uri());

    // When
    let result = adapter
        .notify_arrival(&ArrivalNotification::new(
            UnLocode::new("JPTYO").unwrap(),
            VoyageNumber::new("V002").unwrap(),
        ))
        .await
        .unwrap();

    // Then: 代替港リストが返却される
    assert!(!result.is_accepted());
    assert_eq!(
        result.alternative_ports(),
        &[UnLocode::new("JPYOK").unwrap(), UnLocode::new("JPKOB").unwrap()]
    );
}
```

#### NotificationPort: メール通知（202 Accepted・失敗ログ）

```rust
#[tokio::test]
async fn メール通知送信が202Acceptedを返す() {
    // Given: 呼び出し回数 1 回を期待するスタブ
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/notifications/email"))
        .respond_with(ResponseTemplate::new(202))
        .expect(1)
        .mount(&server)
        .await;
    let adapter = NotificationAdapter::new(server.uri());

    // When: 通知送信を実行する
    let result = adapter
        .send_email(&EmailNotification::new(
            "customer@example.com",
            "貨物が到着しました",
            "...",
        ))
        .await;

    // Then: エラーにならず、スタブが 1 回呼び出される（drop 時に expect(1) を検証）
    assert!(result.is_ok());
}

#[tokio::test]
async fn 通知失敗時にログを記録して処理を継続する() {
    // Given: 通知サービスがエラーを返す（非クリティカルなのでエラーを伝播させない）
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/api/notifications/email"))
        .respond_with(ResponseTemplate::new(503))
        .mount(&server)
        .await;
    let adapter = NotificationAdapter::new(server.uri());

    // When & Then: Err を返さない（ログのみ記録）
    let result = adapter
        .send_email(&EmailNotification::new("customer@example.com", "通知テスト", "..."))
        .await;
    assert!(result.is_ok());
}
```

---

## 5. ユーザーストーリーとテストのトレーサビリティ

| US | タイトル | ユニットテスト | 統合テスト | E2E テスト | 優先度 |
|---|---|---|---|---|---|
| US01 | 輸送見積を作成する | `QuotationService`、`Quotation` 値オブジェクト | `ExternalRoutingServicePort` wiremock | - | 高 |
| US02 | 荷主を登録する | `Shipper` 集約、`ShipperRegistrationService` | `ShipperRepository`、shipper ハンドラ | - | 高 |
| US03 | 法人荷主を登録する | `CorporateShipper` 集約、法人割引率計算 | `CorporateShipperRepository`、shipper ハンドラ | - | 高 |
| US04 | 貨物予約を登録する | `Cargo` 集約、`BookingStatus` 初期遷移 | `CargoRepository`、booking ハンドラ | - | 高 |
| US05 | 危険物・冷凍貨物の予約を登録する | `Cargo` 集約（危険物フラグ）、`CargoCategory` 値オブジェクト | `CargoRepository`、booking ハンドラ | - | 高 |
| US06 | 最適ルートを検索する | `RoutingService`、`Itinerary` 値オブジェクト | `ExternalRoutingServicePort` wiremock（正常・タイムアウト） | - | 高 |
| US07 | ルートを選択して予約に紐付ける | `Cargo::assign_route()`、`BookingStatus::RouteProposed` 遷移 | `CargoRepository`（ルート保存）、routing ハンドラ | - | 高 |
| US08 | 予約を確定する | `Cargo::confirm_booking()`、`BookingStatus::Confirmed` 遷移 | booking ハンドラ（確定 API）、`CargoRepository` | **US08 シナリオ** | 高 |
| US09 | 追跡番号を発行する | `TrackingId` 値オブジェクト（一意性）、`TrackingIdGenerator` | `CargoRepository`（追跡番号保存） | - | 高 |
| US10 | 荷役作業を記録する | `HandlingActivity` 集約、MISROUTED 判定ロジック | `HandlingActivityRepository`、handling ハンドラ | **US10 シナリオ** | 高 |
| US11 | 引取作業を記録する | `HandlingActivity`（RECEIVED イベント） | handling ハンドラ（引取 API） | - | 高 |
| US12 | 貨物状態を手動更新する | `TrackingActivity`、`TransportStatus` 遷移（9 値） | tracking ハンドラ（手動更新 API） | - | 高 |
| US13 | 追跡情報を照会する | - | `TrackingQueryService`（CQRS 読み取り）、tracking ハンドラ | **US13 シナリオ** | 高 |
| US14 | 遅延例外を処理する | `TrackingExceptionEvent` エスカレーション判定 | tracking ハンドラ（例外処理 API）、`NotificationPort` wiremock | - | 高 |
| US15 | 破損・紛失例外を処理する | `HandlingException` 集約、`ExceptionType` 値オブジェクト | handling ハンドラ（例外記録 API）、`CustomsClearancePort` wiremock | - | 高 |
| US16 | 輸送料金を算出する | `Invoice` 集約、`FreightCalculationService`、消費税計算 | `InvoiceRepository`、billing ハンドラ | - | 中 |
| US17 | 法人割引を適用する | `DiscountPolicy` 値オブジェクト、法人割引率計算ロジック | billing ハンドラ（割引適用 API）、`PaymentGatewayPort` wiremock | - | 中 |
| US18 | 精算を処理する | `Invoice::settle()`、`InvoiceStatus` 遷移 | billing ハンドラ（精算 API）、`PaymentGatewayPort` wiremock（正常・失敗） | - | 中 |

---

## 6. カバレッジ目標とメトリクス

### 6.1 レイヤー別カバレッジ目標

| レイヤー | 行カバレッジ目標 | 分岐カバレッジ目標 | 計測ツール |
|---|---|---|---|
| ドメイン層（`domain` クレート） | **85% 以上** | **80% 以上** | cargo-llvm-cov / SonarQube |
| アプリケーション層（`application` クレート） | **80% 以上** | **75% 以上** | cargo-llvm-cov / SonarQube |
| インフラ層 - Repository（`infrastructure::persistence`） | **75% 以上** | — | cargo-llvm-cov / SonarQube |
| インフラ層 - ハンドラ（`infrastructure::web`） | **70% 以上** | — | cargo-llvm-cov / SonarQube |

カバレッジは `cargo llvm-cov --workspace --lcov` で計測し、LCOV レポートを SonarQube および PR コメントに連携する。

> **注記（目標値のキャリブレーション）**: 上記の目標値（85% / 80% など）は初期仮説である。Rust では型で不正状態を排除する（newtype・網羅的 `match`・`Result`）ほど実行時分岐そのものが減り、分岐カバレッジの分母が変わる。また cargo-llvm-cov（LLVM ソースベースカバレッジ）の分岐計測は `match` アームや `?` 演算子の展開の扱いが JaCoCo などの JVM 系ツールと異なるため、数値を他言語プロジェクトと直接比較しない。目標値は最初の 2〜3 イテレーションの実測値でキャリブレーションし、乖離が大きい場合は ADR を起票して改訂する。

### 6.2 SonarQube Quality Gate 条件

| 条件 | 基準値 | 適用対象 |
|---|---|---|
| 行カバレッジ（新規コード） | **80% 以上** | 新規追加コード |
| 重複コード率 | **3% 以下** | プロジェクト全体 |
| Reliability Rating | **A**（バグゼロ） | プロジェクト全体 |
| Security Rating | **A**（脆弱性ゼロ） | プロジェクト全体 |
| Maintainability Rating | **A** | 新規コード |
| Security Hotspot Review | **100%** | 新規コード |

Quality Gate が失敗した場合、PR のマージをブロックする。加えて `cargo clippy -- -D warnings`（deny warnings）と `cargo fmt --check` を CI の必須ゲートとする。

---

## 7. CI/CD とのテスト連携

### 7.1 ステージ別テスト戦略

| ステージ | テスト種別 | 目標時間 | 失敗時の扱い |
|---|---|---|---|
| コミット（ローカル） | ユニットテスト + clippy + rustfmt | **< 60 秒** | コミット前に修正 |
| PR | ユニット + 統合 + cargo-deny + SonarQube | **< 5 分** | PR マージ不可 |
| main ブランチマージ後 | E2E テスト | **< 15 分** | Slack 通知（ホットフィックス優先） |
| リリース | 全テスト + パフォーマンステスト | **< 30 分** | リリース停止 |

#### 統合テストの DB 供給方式

統合テストの PostgreSQL 供給は **testcontainers-rs に一本化**する。テストコード自身がコンテナを起動するため、ローカルでも CI でも同一挙動になり、CI ワークフロー側に PostgreSQL サービスコンテナを定義しない（Docker が利用可能な runner であれば追加設定なしで動作する）。例外は `cargo sqlx prepare --check` による `.sqlx` 鮮度検証のみで、これはビルド時に `DATABASE_URL` 経由の DB 接続を必要とするため、専用の `sqlx-check` ジョブに分離して軽量なサービスコンテナを使用する（詳細は architecture_infrastructure.md の CI ワークフロー例を参照）。テスト・ビルドジョブのコンパイルは `SQLX_OFFLINE=true` で `.sqlx` キャッシュにより完結させる。

### 7.2 GitHub Actions パイプライン図

```plantuml
@startuml
!theme plain

|ローカル|
start
:git commit;
:ユニットテスト\n(cargo test + mockall)\n< 30 秒;
:clippy (-D warnings)\n+ rustfmt --check\n< 30 秒;
if (テスト成功?) then (yes)
  :コミット完了;
else (no)
  :修正してリトライ;
  stop
endif

|PR|
:git push / PR 作成;
fork
  :ユニットテスト\n< 2 分;
fork again
  :統合テスト\n(testcontainers-rs + oneshot\n + wiremock)\n< 3 分;
fork again
  :cargo deny check\n< 30 秒;
end fork
:cargo llvm-cov 計測\n+ SonarQube 解析\nQuality Gate チェック;
if (全テスト + Quality Gate 成功?) then (yes)
  :PR マージ許可;
else (no)
  :PR マージ不可\n（コメントで詳細通知）;
  stop
endif

|main ブランチ|
:main マージ;
:E2E テスト\n(Playwright)\n< 15 分;
if (E2E 成功?) then (yes)
  :デプロイ続行;
else (no)
  :Slack 通知\nホットフィックス対応;
  stop
endif

|リリース|
:リリースタグ作成;
:全テストスイート実行;
:パフォーマンステスト\n< 10 分;
if (全テスト成功?) then (yes)
  :リリース承認;
  stop
else (no)
  :リリース停止\nインシデント対応;
  stop
endif

@enduml
```

---

## 8. TDD 開発ワークフロー

### 8.1 インサイドアウト TDD（バックエンド）

ドメイン層から外側に向かって開発する。外部依存を後回しにすることで、ビジネスロジックに集中できる。

```plantuml
@startuml
!theme plain

start
:ユーザーストーリーを確認する\n（受入条件を把握する）;

group Step 1: ドメインモデルのユニットテスト
  :【RED】集約・値オブジェクトの\nテストを書く\n（Given-When-Then 形式、#[cfg(test)]）;
  :【GREEN】最小限の実装でテストを通す;
  :【REFACTOR】重複除去・命名改善\n（clippy の指摘も解消）;
end group

group Step 2: アプリケーション層のユニットテスト
  :【RED】ユースケースサービスの\nテストを書く\n（ポート trait は mockall でモック）;
  :【GREEN】ポート trait を\n定義してサービスを実装する;
  :【REFACTOR】サービスのオーケストレーションを整理;
end group

group Step 3: アダプターの統合テスト
  :【RED】Repository / ハンドラ /\n外部 ACL アダプターの\n統合テストを tests/ に書く;
  :【GREEN】testcontainers-rs / oneshot /\nwiremock で実装する;
  :【REFACTOR】クエリ最適化・エラーハンドリング整理;
end group

:cargo build でクレート依存ルール検証\n（+ cargo deny check）;
:全テストが GREEN であることを確認してコミット;
stop

@enduml
```

### 8.2 重要なビジネスルール（必ず TDD 適用）

以下のビジネスルールは複雑度が高く、テストファーストで実装しなければならない。

#### Cargo の BookingStatus 状態遷移（8 値）

```
Preliminary → RouteProposed → Confirmed → TrackingIssued
    → InTransit → Delivered → Settled
    ↘ Cancelled（キャンセル）
```

状態遷移は `enum BookingStatus` と網羅的 `match` で実装し、遷移漏れをコンパイラが検出できるようにする。なお、誤送（Misrouted）は Routing Context の `RoutingStatus::Misrouted`、通関状態は Handling Context の `CustomsStatus` が担うため、`BookingStatus` には含めない。

テスト観点:

- 各遷移の正常系（許可されている遷移）
- 各遷移の異常系（許可されていない遷移 → `Err(BookingDomainError::InvalidStatusTransition)`）
- 終端状態（Settled・Cancelled）からの遷移拒否

#### HandlingActivity の荷役妥当性検証（MISROUTED 判定）

```rust
#[test]
fn 指定ルート外の港で荷役を実行するとMISROUTED判定になる() {
    // Given: 東京→ハンブルク のルートを持つ貨物
    let mut cargo = CargoFixture::with_route(RouteFixture::tokyo_to_hamburg());

    // When: ルートに含まれないシンガポールで荷役を記録する
    let activity = HandlingActivity::new(
        cargo.tracking_id().clone(),
        UnLocode::new("SGSIN").unwrap(), // ルート外の港
        HandlingType::Load,
        OffsetDateTime::now_utc(),
    );

    // Then: ルーティング状態が Misrouted に遷移する（Routing Context の責務）
    cargo.apply_handling_activity(&activity);
    assert_eq!(cargo.routing_status(), RoutingStatus::Misrouted);
}
```

##### MISROUTED 判定のデシジョンテーブルテスト

MISROUTED 判定は「荷役種別 × 場所の一致 × VoyageNumber の有無」の条件組み合わせで挙動が分かれるため、デシジョンテーブルとして固定する。前提仕様: Load / Unload は VoyageNumber 必須（`None` はバリデーションエラー）、Customs は場所非依存（どの場所で記録しても MISROUTED にならない）。

| # | 荷役種別 | VoyageNumber | 場所とルート（Itinerary の Leg）の関係 | 期待結果 |
|---|---|---|---|---|
| 1 | Load | あり（一致） | Leg の出発港と一致 | OnTrack のまま |
| 2 | Load | あり（一致） | ルート外の港 | **Misrouted** |
| 3 | Load | あり（不一致の航海番号） | Leg の出発港と一致 | **Misrouted** |
| 4 | Load | **None** | — | **バリデーションエラー**（記録自体を拒否） |
| 5 | Unload | あり（一致） | Leg の到着港と一致 | OnTrack のまま |
| 6 | Unload | あり（一致） | ルート外の港 | **Misrouted** |
| 7 | Unload | **None** | — | **バリデーションエラー**（記録自体を拒否） |
| 8 | Customs | なし（不要） | Leg 上の港 | OnTrack のまま（場所非依存） |
| 9 | Customs | なし（不要） | ルート外の港 | OnTrack のまま（場所非依存） |
| 10 | Load | あり（一致） | 複数 Leg のうち後続 Leg の出発港と一致（部分一致） | OnTrack のまま（いずれかの Leg に一致すれば妥当） |
| 11 | Load | あり（一致） | 複数 Leg のいずれの出発港とも不一致 | **Misrouted** |

このテーブルは `rstest` のパラメタライズドテストで 1 行 = 1 ケースとして実装し、仕様変更時はまずテーブルを更新してから実装を追随させる。

#### Invoice の料金計算（法人割引・消費税計算）

```rust
#[test]
fn 法人割引10パーセントと消費税10パーセントが正しく計算される() {
    // Given: 基本料金 100,000 円、法人割引率 10% の Invoice
    let base_amount = Money::jpy(dec!(100_000));
    let corporate_discount = DiscountPolicy::corporate(Percentage::new(10).unwrap());

    // When: 料金を確定する
    let invoice = Invoice::calculate(base_amount, corporate_discount, TaxRate::STANDARD);

    // Then: 割引後 90,000 円 × 消費税 10% = 99,000 円
    assert_eq!(invoice.net_amount(), Money::jpy(dec!(90_000)));
    assert_eq!(invoice.tax_amount(), Money::jpy(dec!(9_000)));
    assert_eq!(invoice.total_amount(), Money::jpy(dec!(99_000)));
}
```

金額は浮動小数点を使わず、`rust_decimal::Decimal` と通貨コードの組で保持する。`add` は通貨不一致時に `DomainError` を返す `Result<Money, DomainError>` として設計する。演算則（結合性・非負性）は proptest でも検証する。

##### 消費税の端数処理と適用順序のテーブル駆動テスト

料金計算は「基本運賃 + 燃油サーチャージ → 法人割引（上限 30%）→ 消費税 10%」の順で適用し、丸め（1 円未満切り捨て）は最終段階の消費税計算で 1 回だけ行う。この仕様をテーブル駆動テストで固定する。

端数処理の境界値テスト（課税対象額 = サーチャージ加算・割引適用後の金額）：

| ケース | 課税対象額 | 税額（10%、1 円未満切り捨て） | 合計 | 意図 |
|---|---|---|---|---|
| 丸めなし | 100,000 円 | 10,000 円 | 110,000 円 | 基準ケース |
| 切り捨て発生 | 99,999 円 | 9,999 円（9,999.9 → 切り捨て） | 109,998 円 | 端数切り捨ての確認 |
| 丸め境界 +1 円 | 100,001 円 | 10,000 円（10,000.1 → 切り捨て） | 110,001 円 | 境界直上の確認 |
| 丸め境界 −1 円 | 99,990 円 | 9,999 円 | 109,989 円 | 境界直下の確認 |
| 割引上限 | 割引率 30% 超を指定 | — | バリデーションエラー | 法人割引の上限 30% を固定 |

適用順序の固定テスト（順序が変わると結果が変わる入力を選んで検証する）：

| ケース | 基本運賃 | サーチャージ | 割引率 | 正しい順序の結果 | 誤った順序（税 → 割引）の結果 |
|---|---|---|---|---|---|
| 順序固定 | 100,000 円 | 5,555 円 | 10% | 課税対象 94,999 円（1 円未満切り捨てなしの中間値は丸めない）→ 税 9,499 円 | 中間丸めや順序入れ替えで ±1 円ずれる |

中間段階（サーチャージ加算・割引適用）では `Decimal` のまま丸めずに保持し、最終段階でのみ切り捨てることをアサーションで固定する。これらのテーブルは `rstest` のパラメタライズドテストまたは配列 + ループのテーブル駆動テストで実装し、proptest で「税額 = floor(課税対象 × 0.1)」の性質を補完的に検証する。

#### TrackingExceptionEvent のエスカレーション判定

```rust
#[test]
fn 遅延が48時間を超える場合にエスカレーションフラグが立つ() {
    // Given: 遅延 72 時間の例外イベント
    let event = TrackingExceptionEvent::delay(
        TrackingId::new("CARGO-001").unwrap(),
        Duration::hours(72),
    );

    // When: エスカレーション判定を実行する
    let result = EscalationPolicy::default().evaluate(&event);

    // Then: エスカレーション対象と判定される
    assert!(result.requires_escalation());
    assert_eq!(result.escalation_level(), EscalationLevel::Critical);
}

#[test]
fn 遅延が48時間以内の場合はエスカレーション不要と判定される() {
    // Given: 遅延 24 時間の例外イベント
    let event = TrackingExceptionEvent::delay(
        TrackingId::new("CARGO-002").unwrap(),
        Duration::hours(24),
    );

    // When
    let result = EscalationPolicy::default().evaluate(&event);

    // Then
    assert!(!result.requires_escalation());
}
```

### 8.3 Bounded Context 別 TDD 優先順位

| Bounded Context | TDD 優先ルール | 理由 |
|---|---|---|
| Booking Context | BookingStatus 遷移（8 値）を最初にテストする | 最も複雑な状態機械。`enum` + 網羅的 `match` で遷移表を型に落とし込み、バグの影響範囲を最小化する |
| Routing Context | ExternalRoutingServicePort のフォールバックをテストする | 外部依存が本番障害の主要因になりやすい |
| Tracking Context | CQRS 読み取りクエリのパフォーマンスを統合テストで検証する | 30 秒ポーリングの負荷を事前に確認する |
| Handling Context | MISROUTED 判定ロジックを先にテストする | 荷役記録ミスは運用上重大なインシデントになる |
| Billing Context | 割引・消費税計算をテーブル駆動テスト + proptest で網羅する | 金額計算のバグは法的リスクを伴う |
| Shared Domain | Location（UN/LOCODE）のバリデーションを値オブジェクト（newtype）レベルで担保する | 全コンテキストが共有するため、バグの影響範囲が広い。proptest で不正入力の拒否を網羅検証する |
