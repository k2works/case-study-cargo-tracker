---
title: インフラアーキテクチャ
description: 国際貨物輸送管理システム（Rust 版）のインフラアーキテクチャ設計。Docker/AWS ECS/RDS 構成、CI/CD パイプライン、監視設計を定義する。
published: true
date: 2026-07-06T10:00:00.000Z
tags: architecture, infrastructure, aws, ecs, docker, terraform, github-actions, rust, axum
---

# インフラアーキテクチャ - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムのインフラアーキテクチャを定義する。
コンテナ化された Rust（axum）アプリケーションを **AWS ECS Fargate** 上で稼働させ、
**RDS PostgreSQL** でデータを永続化する。
**Terraform** によるインフラの IaC 管理と **GitHub Actions** による CI/CD パイプラインを整備する。

Rust の静的リンクバイナリは JVM と比較して起動が高速（サブ秒）かつメモリフットプリントが小さいため、
コンテナイメージの縮小・タスクサイズの削減・スケールアウト時のコールドスタート改善が期待できる。

## インフラ構成図

```plantuml
@startuml
title AWS インフラ構成図

cloud "AWS" {

  package "VPC (10.0.0.0/16)" as vpc {

    package "Public Subnet (10.0.1.0/24, 10.0.2.0/24)" as public {
      [Application Load Balancer\n(ALB)]
      [NAT Gateway]
    }

    package "Private Subnet - App (10.0.10.0/24, 10.0.11.0/24)" as private_app {
      [ECS Fargate\ncargo-tracker-app\n(Rust / axum)]
    }

    package "Private Subnet - DB (10.0.20.0/24, 10.0.21.0/24)" as private_db {
      database "RDS PostgreSQL 16\n(Multi-AZ)\nPrimary"
      database "RDS PostgreSQL 16\n(Multi-AZ)\nStandby"
    }
  }

  package "AWS Services" as services {
    [ECR\n(コンテナイメージ)]
    [Secrets Manager\n(DB 接続情報)]
    [CloudWatch Logs\n(アプリログ)]
    [CloudWatch Metrics\n(メトリクス)]
    [CloudWatch Alarms\n(アラート)]
    [S3\n(Terraform State)]
    [Route 53\n(DNS)]
    [ACM\n(TLS 証明書)]
  }
}

actor "ユーザー" as user
actor "開発者\n(GitHub Actions)" as developer

user --> [Route 53\n(DNS)] : HTTPS
[Route 53\n(DNS)] --> [Application Load Balancer\n(ALB)] : HTTPS (443)
[Application Load Balancer\n(ALB)] --> [ECS Fargate\ncargo-tracker-app\n(Rust / axum)] : HTTP (8080)
[ECS Fargate\ncargo-tracker-app\n(Rust / axum)] --> [RDS PostgreSQL 16\n(Multi-AZ)\nPrimary] : 5432
[ECS Fargate\ncargo-tracker-app\n(Rust / axum)] --> [Secrets Manager\n(DB 接続情報)]
[ECS Fargate\ncargo-tracker-app\n(Rust / axum)] --> [CloudWatch Logs\n(アプリログ)]
[RDS PostgreSQL 16\n(Multi-AZ)\nPrimary] --> [RDS PostgreSQL 16\n(Multi-AZ)\nStandby] : 同期レプリケーション
[NAT Gateway] --> [ECR\n(コンテナイメージ)] : イメージ Pull
[ECS Fargate\ncargo-tracker-app\n(Rust / axum)] --> [NAT Gateway] : 外部通信

developer --> [ECR\n(コンテナイメージ)] : docker push
developer --> [S3\n(Terraform State)] : terraform state

@enduml
```

## コンテナ化戦略

### Dockerfile 設計方針

マルチステージビルドを採用し、本番イメージのサイズを最小化する。
Rust の静的リンクバイナリはランタイム依存が最小限であるため、
**distroless/cc**（または debian-slim）ベースの数十 MB 級イメージを実現できる。
依存クレートのビルドキャッシュには **cargo-chef** を使用する。

```plantuml
@startuml
title Dockerfile マルチステージビルド

package "Stage 1: Planner / Builder" as build {
  [rust:slim + cargo-chef\n（ビルドステージ）] as builder
}

package "Stage 2: Runtime" as runtime {
  [gcr.io/distroless/cc\n（実行ステージ）] as runner
}

note right of builder : cargo chef prepare / cook で\n依存クレートをレイヤーキャッシュ\ncargo build --release でバイナリ生成
note right of runner : シェル・パッケージマネージャなし\nリリースバイナリのみコピー\n非 root ユーザーで実行

builder --> runner : COPY --from=builder

@enduml
```

Dockerfile の設計原則：

| 原則 | 内容 |
| :--- | :--- |
| **マルチステージビルド** | ビルドツールチェーン（rustc / cargo）を本番イメージに含めない |
| **cargo-chef による依存キャッシュ** | `cargo chef prepare` → `cook` で依存クレートのビルドをレイヤーキャッシュし、ソース変更時の再ビルドを最小化 |
| **非 root ユーザー** | distroless の `nonroot` ユーザー（または `USER` 指定）で実行 |
| **ヘルスチェック** | axum で実装した `/health` エンドポイントを ALB ヘルスチェックに使用（distroless にはシェルがないため `HEALTHCHECK` は ALB / ECS 側で実施） |
| **環境変数による設定** | DB 接続情報等は実行時環境変数（Secrets Manager 連携）で注入。コードにハードコードしない |
| **sqlx オフラインビルド** | sqlx の `query!` / `query_as!` マクロはビルド時に DB 接続または `.sqlx` オフラインキャッシュを必要とする。Docker ビルド中は DB に接続できないため、`SQLX_OFFLINE=true` を設定し、`cargo sqlx prepare` で生成した `.sqlx` ディレクトリ（リポジトリにコミット）を使用する |

Dockerfile の例：

```dockerfile
# Stage 1: 依存解決プランの作成
FROM rust:slim AS chef
RUN cargo install cargo-chef
WORKDIR /app

FROM chef AS planner
COPY . .
RUN cargo chef prepare --recipe-path recipe.json

# Stage 2: 依存クレートのビルド（キャッシュ層）＋アプリビルド
FROM chef AS builder
# sqlx をオフラインモードでビルドする（ビルド時に DB 接続しない）
ENV SQLX_OFFLINE=true
COPY --from=planner /app/recipe.json recipe.json
RUN cargo chef cook --release --recipe-path recipe.json
# .sqlx（cargo sqlx prepare の生成物）を含むソース一式をコピー
COPY . .
RUN cargo build --release --bin cargo-tracker-app

# Stage 3: 実行イメージ（distroless）
FROM gcr.io/distroless/cc-debian12:nonroot
WORKDIR /app
COPY --from=builder /app/target/release/cargo-tracker-app /app/cargo-tracker-app
EXPOSE 8080
ENTRYPOINT ["/app/cargo-tracker-app"]
```

cargo-chef キャッシュ層と `.sqlx` の関係：

- `cargo chef cook` は依存クレートのみをビルドするため `.sqlx` を必要とせず、キャッシュ層は `.sqlx` の変更に影響されない。`.sqlx` は `COPY . .` 以降のアプリビルド層でのみ参照される。
- SQL クエリ（`query!` / `query_as!` の対象）を追加・変更した際は、開発環境で DB に接続した状態で `cargo sqlx prepare --workspace` を再実行し、更新された `.sqlx` ディレクトリをコミットする。これを怠ると `SQLX_OFFLINE=true` のビルドが失敗する。

### Docker Compose ローカル開発環境

```plantuml
@startuml
title Docker Compose ローカル開発環境

package "docker-compose.yml" as compose {

  package "app サービス" as app_service {
    [cargo-tracker-app\n(Rust / axum)\nport: 8080]
  }

  package "db サービス" as db_service {
    database "postgres:16-alpine\nport: 5432\nvolume: postgres_data"
  }

  package "開発支援サービス" as dev_services {
    [adminer\n(DB 管理 UI)\nport: 8081]
    [mailhog\n(メール確認)\nport: 8025]
  }
}

[cargo-tracker-app\n(Rust / axum)\nport: 8080] --> [postgres:16-alpine\nport: 5432\nvolume: postgres_data] : DATABASE_URL
[adminer\n(DB 管理 UI)\nport: 8081] --> [postgres:16-alpine\nport: 5432\nvolume: postgres_data]

note bottom of compose
  ローカル開発時も本番と同じ
  PostgreSQL を使用することで
  本番との差異を最小化する
  sqlx migrate による
  マイグレーションも
  ローカルで検証可能
end note

@enduml
```

## AWS 構成

### ECS Fargate 構成

| 設定項目 | 値 | 説明 |
| :--- | :--- | :--- |
| クラスター名 | `cargo-tracker-cluster` | ECS クラスター |
| タスク定義 | `cargo-tracker-app` | Rust（axum）コンテナ定義 |
| CPU | 256 (0.25 vCPU) | 初期設定。Rust はフットプリントが小さいため JVM 構成の半分から開始し、負荷に応じてスケール |
| メモリ | 512 MB | GC ヒープが不要なため小さく設定可能。実測に基づき調整 |
| 希望タスク数 | 2 | 最小稼働台数（高可用性） |
| 最大タスク数 | 6 | Auto Scaling 上限 |
| サービスディスカバリ | ALB ターゲットグループ | ヘルスチェック経由でルーティング |

> Rust バイナリは起動がサブ秒で完了しウォームアップも不要なため、
> スケールアウト時のコールドスタートが実質的に発生せず、
> デプロイ・オートスケーリングのリードタイムが短縮される。

### VPC・ネットワーク設計

```plantuml
@startuml
title VPC ネットワーク設計

package "VPC: 10.0.0.0/16" as vpc {

  package "AZ-1a" as az1 {
    [Public Subnet\n10.0.1.0/24] as pub1
    [Private Subnet (App)\n10.0.10.0/24] as priv_app1
    [Private Subnet (DB)\n10.0.20.0/24] as priv_db1
  }

  package "AZ-1c" as az2 {
    [Public Subnet\n10.0.2.0/24] as pub2
    [Private Subnet (App)\n10.0.11.0/24] as priv_app2
    [Private Subnet (DB)\n10.0.21.0/24] as priv_db2
  }

  [Internet Gateway] as igw
  [NAT Gateway\n(AZ-1a)] as nat1
  [NAT Gateway\n(AZ-1c)] as nat2
  [ALB] as alb
}

[Internet] --> igw
igw --> alb
alb --> priv_app1
alb --> priv_app2
priv_app1 --> nat1
priv_app2 --> nat2
nat1 --> igw
nat2 --> igw
priv_app1 --> priv_db1
priv_app2 --> priv_db2

note right of priv_app1
  ECS Fargate タスクは
  Private Subnet で稼働
  直接インターネットに露出しない
end note

note right of priv_db1
  RDS は DB 専用 Private Subnet
  App Subnet からのみアクセス可能
  Security Group で制御
end note

@enduml
```

### セキュリティグループ設計

| SG 名 | インバウンドルール | アウトバウンドルール | 用途 |
| :--- | :--- | :--- | :--- |
| `sg-alb` | 0.0.0.0/0: 443 (HTTPS) | ECS SG: 8080 | ALB |
| `sg-ecs-app` | ALB SG: 8080 | RDS SG: 5432, 0.0.0.0/0: 443 | ECS Fargate タスク |
| `sg-rds` | ECS SG: 5432 | - | RDS PostgreSQL |

### RDS 構成

| 設定項目 | 値 | 説明 |
| :--- | :--- | :--- |
| エンジン | PostgreSQL 16.x | 本番 DB |
| インスタンスクラス | `db.t3.medium` | 初期設定 |
| マルチ AZ | 有効 | フェイルオーバー対応 |
| 自動バックアップ | 7 日間保持 | 日次スナップショット |
| 暗号化 | 有効（AWS KMS） | データの暗号化 |
| パラメータグループ | カスタム | `shared_buffers`、`max_connections` 等の最適化 |

## CI/CD パイプライン

```plantuml
@startuml
title GitHub Actions CI/CD パイプライン

|GitHub|
start
:Pull Request / Push to main;

|CI ワークフロー (ci.yml)|
:コードチェックアウト;
:Rust ツールチェーンセットアップ\n(Swatinem/rust-cache);
fork
  :Lint\n(cargo clippy / cargo fmt --check);
fork again
  :単体テスト\n(cargo test);
fork again
  :セキュリティ監査\n(cargo-audit);
end fork
:統合テスト\n(testcontainers-rs がテストコード内で\nPostgreSQL コンテナを自動起動);
:ビルド\n(cargo build --release);
:コードカバレッジレポート\n(cargo-llvm-cov);

if (すべてのチェックが通過?) then (yes)
  |CD ワークフロー (cd.yml)|
  :Docker イメージビルド\n(cargo-chef キャッシュ利用);
  :ECR へ Push\n(タグ: git SHA);

  if (ブランチが main?) then (yes)
    :ステージング環境へデプロイ\n(ECS Rolling Update);
    :ステージング E2E テスト;

    if (手動承認) then (approved)
      :本番環境へデプロイ\n(ECS Rolling Update);
      :デプロイ完了通知\n(Slack / GitHub);
    else (rejected)
      :デプロイキャンセル;
    endif
  else (feature branch)
    :ステージング環境へのデプロイはスキップ;
  endif
else (no)
  :CI 失敗通知;
  stop
endif

stop

@enduml
```

### GitHub Actions ワークフロー構成

| ワークフロー | トリガー | 内容 |
| :--- | :--- | :--- |
| `ci.yml` | PR / push | clippy・rustfmt・cargo test・cargo-audit・カバレッジ（cargo-llvm-cov） |
| `cd-staging.yml` | main push | ステージング環境への自動デプロイ |
| `cd-production.yml` | 手動承認 / release タグ | 本番環境へのデプロイ |
| `terraform-plan.yml` | infra/ 変更の PR | `terraform plan` 結果を PR にコメント |
| `terraform-apply.yml` | infra/ 変更の main push | `terraform apply` でインフラ更新 |

AWS 認証は GitHub Actions **OIDC** による一時クレデンシャルを使用し、長期アクセスキーは保存しない。

統合テストの DB 供給は **testcontainers-rs に一本化**する。テストコード自身が PostgreSQL コンテナを起動するため、ローカルでも CI でも同一挙動になり、`check` ジョブには PostgreSQL サービスコンテナを定義しない。ubuntu-latest など Docker が利用可能な runner であれば追加設定なしで testcontainers-rs が動作する。ただし `cargo sqlx prepare --check` はビルド時に `DATABASE_URL` 経由の DB 接続を必要とするため、この検証のみ専用の `sqlx-check` ジョブに分離し、軽量なサービスコンテナを使用する。

CI ワークフローの例：

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  check:
    # Docker が利用可能な runner（testcontainers-rs が統合テスト内で PostgreSQL を自動起動）
    runs-on: ubuntu-latest
    env:
      SQLX_OFFLINE: "true" # コンパイルは .sqlx オフラインキャッシュで完結させる
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
        with:
          components: clippy, rustfmt
      - uses: Swatinem/rust-cache@v2
      - name: フォーマットチェック
        run: cargo fmt --all -- --check
      - name: Lint
        run: cargo clippy --all-targets --all-features -- -D warnings
      - name: セキュリティ監査
        run: cargo install cargo-audit && cargo audit
      - name: テスト（統合テストは testcontainers-rs が PostgreSQL コンテナを自動起動）
        run: cargo test --all-features
      - name: カバレッジ
        run: |
          cargo install cargo-llvm-cov
          cargo llvm-cov --all-features --lcov --output-path lcov.info

  # .sqlx 鮮度検証のみビルド時に DATABASE_URL が必要なため、サービスコンテナを使う
  sqlx-check:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_PASSWORD: postgres
          POSTGRES_DB: cargotracker_test
        ports: ["5432:5432"]
        options: >-
          --health-cmd "pg_isready" --health-interval 10s
          --health-timeout 5s --health-retries 5
    env:
      DATABASE_URL: postgres://postgres:postgres@localhost:5432/cargotracker_test
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - uses: Swatinem/rust-cache@v2
      - name: マイグレーション適用
        run: cargo install sqlx-cli --no-default-features --features postgres && sqlx migrate run
      - name: .sqlx 鮮度検証（オフラインキャッシュとクエリの整合性チェック）
        run: cargo sqlx prepare --check --workspace
```

`SQLX_OFFLINE` の使い分け：

| 場面 | 設定 | 理由 |
| :--- | :--- | :--- |
| CI の `sqlx-check` ジョブ | 未設定（DB 接続） | `DATABASE_URL` で PostgreSQL サービスコンテナに接続し、`cargo sqlx prepare --check --workspace` で `.sqlx` の鮮度を検証する |
| Docker イメージビルド | `SQLX_OFFLINE=true` | ビルド中は DB に接続できないため、コミット済みの `.sqlx` キャッシュを使用する |
| CI の `check` ジョブ（Lint・テスト・カバレッジ） | `SQLX_OFFLINE=true` | コンパイルは `.sqlx` キャッシュで完結させる。統合テストが必要とする実 DB は testcontainers-rs がテストコード内で起動する |

### デプロイ戦略

| 戦略 | 環境 | 内容 |
| :--- | :--- | :--- |
| **ローリングアップデート** | ステージング・本番 | ECS の `minimumHealthyPercent: 50`, `maximumPercent: 200` でゼロダウンタイムデプロイ。Rust の高速起動によりロールアウト時間が短い |
| **ロールバック** | 本番 | 前バージョンの ECR イメージ SHA で `ecs update-service` を実行 |
| **Blue/Green** | 将来対応 | CodeDeploy + ALB の B/G デプロイ（高トラフィック時に検討） |

DB マイグレーションは **sqlx migrate** をデプロイ時に実行する（アプリケーション起動時の `sqlx::migrate!` 埋め込み実行、または ECS タスクとしての事前実行）。

## 監視・ログ設計

```plantuml
@startuml
title 監視・ログアーキテクチャ

package "ECS Fargate (cargo-tracker-app)" as app {
  [axum\n(tracing + metrics)]
}

package "AWS CloudWatch" as cw {
  [CloudWatch Logs\n(/ecs/cargo-tracker)]
  [CloudWatch Metrics\n(カスタムメトリクス)]
  [CloudWatch Alarms\n(アラート設定)]
  [CloudWatch Dashboard\n(可視化)]
}

package "通知" as notify {
  [SNS Topic]
  [Slack / メール通知]
}

[axum\n(tracing + metrics)] --> [CloudWatch Logs\n(/ecs/cargo-tracker)] : awslogs ドライバー
[CloudWatch Logs\n(/ecs/cargo-tracker)] --> [CloudWatch Metrics\n(カスタムメトリクス)] : ログメトリクスフィルター
[CloudWatch Metrics\n(カスタムメトリクス)] --> [CloudWatch Alarms\n(アラート設定)] : メトリクス監視
[CloudWatch Alarms\n(アラート設定)] --> [SNS Topic] : アラーム発火
[SNS Topic] --> [Slack / メール通知]
[CloudWatch Metrics\n(カスタムメトリクス)] --> [CloudWatch Dashboard\n(可視化)]

note right of [CloudWatch Logs\n(/ecs/cargo-tracker)]
  ログレベル: INFO（本番）
  ログ形式: JSON 構造化ログ
  (tracing-subscriber の json フォーマッター)
  保持期間: 30 日
end note

@enduml
```

アプリケーション側の観測可能性は以下で実装する（Spring Boot Actuator の代替）：

| 機能 | 実装 |
| :--- | :--- |
| 構造化ログ | `tracing` + `tracing-subscriber`（JSON フォーマッター）で stdout へ出力し、awslogs ドライバーで CloudWatch Logs へ転送 |
| ヘルスチェック | axum に `/health`（liveness）と `/health/ready`（DB 接続確認を含む readiness）を自作実装。ALB ターゲットグループのヘルスチェックに使用 |
| メトリクス | `metrics` クレート + `metrics-exporter-prometheus` で `/metrics` を公開、またはログメトリクスフィルターで CloudWatch カスタムメトリクス化 |
| トレース ID | `tracing` の span にリクエストごとの `trace_id` を付与し、全ログに伝播 |

### 監視項目

| 監視カテゴリー | メトリクス | 閾値（例） | アクション |
| :--- | :--- | :--- | :--- |
| **アプリケーション** | HTTP 5xx エラー率 | 5% 以上 | アラート → Slack 通知 |
| **アプリケーション** | レスポンスタイム（P99） | 3 秒以上 | アラート → Slack 通知 |
| **ECS** | CPU 使用率 | 80% 以上 | Auto Scaling トリガー |
| **ECS** | メモリ使用率 | 80% 以上 | アラート |
| **RDS** | DB 接続数 | 上限の 80% | アラート |
| **RDS** | レプリケーション遅延 | 60 秒以上 | アラート |
| **ALB** | HealthyHostCount | 0 | 緊急アラート（PagerDuty 等） |

### ログ設計

| ログ種別 | 出力先 | 形式 | 内容 |
| :--- | :--- | :--- | :--- |
| アプリケーションログ | CloudWatch Logs `/ecs/cargo-tracker` | JSON | リクエスト・ビジネスイベント・エラー |
| アクセスログ | ALB Access Logs → S3 | ALB 形式 | HTTP アクセス履歴 |
| 監査ログ | CloudWatch Logs `/ecs/cargo-tracker/audit` | JSON | 荷役登録・予約変更等の重要操作 |
| DB 低速クエリログ | RDS → CloudWatch Logs | PostgreSQL 形式 | 1 秒以上のクエリ |

構造化ログの形式（tracing-subscriber JSON 出力）：

```json
{
  "timestamp": "2026-07-06T10:00:00.000Z",
  "level": "INFO",
  "trace_id": "abc123",
  "user_id": "user-001",
  "context": "booking",
  "message": "貨物予約を登録しました",
  "booking_id": "BK-2026-0001"
}
```

## 環境構成

```plantuml
@startuml
title 環境構成

package "開発環境 (local)" as local {
  [IDE (RustRover / VS Code)]
  database "Docker Compose\nPostgreSQL:5432"
  [axum\n(localhost:8080)]
}

package "CI 環境 (GitHub Actions)" as ci {
  [GitHub Actions Runner]
  database "PostgreSQL\nサービスコンテナ"
  [テスト・ビルド実行]
}

package "ステージング環境 (AWS)" as staging {
  [ECS Fargate\n(1 タスク)] as stg_ecs
  database "RDS PostgreSQL\n(Single-AZ)" as stg_db
}

note right of stg_db : 本番と同等構成\n自動デプロイ（main push）\n手動テストに使用

package "本番環境 (AWS)" as production {
  [ECS Fargate\n(2+ タスク / Auto Scaling)] as prod_ecs
  database "RDS PostgreSQL\n(Multi-AZ)" as prod_db
}

note right of prod_db : 高可用性構成\n手動承認後デプロイ\n監視・アラート完備

[IDE (RustRover / VS Code)] --> [axum\n(localhost:8080)]
[axum\n(localhost:8080)] --> [Docker Compose\nPostgreSQL:5432]

[GitHub Actions Runner] --> [テスト・ビルド実行]
[テスト・ビルド実行] --> [PostgreSQL\nサービスコンテナ]

@enduml
```

### 環境別設定一覧

| 設定項目 | ローカル | ステージング | 本番 |
| :--- | :--- | :--- | :--- |
| DB | Docker PostgreSQL | RDS（Single-AZ） | RDS（Multi-AZ） |
| ECS タスク数 | - | 1 | 2〜6（Auto Scaling） |
| ログレベル（`RUST_LOG`） | `debug` | `info` | `info` |
| 環境識別（`APP_ENV`） | `local` | `staging` | `production` |
| DB マイグレーション | `sqlx migrate run`（起動時 / 手動） | 自動（デプロイ時） | 自動（デプロイ時） |
| DB リセット（`sqlx database reset`） | 許可 | 禁止 | 禁止 |
| シークレット管理 | `.env` ファイル | AWS Secrets Manager | AWS Secrets Manager |

## Terraform IaC 構成

```plantuml
@startuml
title Terraform ディレクトリ構成

package "ops/terraform/" as terraform {

  package "modules/" as modules {
    [vpc/\n（VPC・サブネット・SG）]
    [ecs/\n（ECS クラスター・サービス・タスク定義）]
    [rds/\n（RDS インスタンス・SG）]
    [alb/\n（ALB・リスナー・ターゲットグループ）]
    [ecr/\n（ECR リポジトリ）]
    [iam/\n（ECS 実行ロール・タスクロール）]
    [monitoring/\n（CloudWatch・Alarms・Dashboard）]
  }

  package "environments/" as envs {
    [staging/\n（main.tf, variables.tf, terraform.tfvars）]
    [production/\n（main.tf, variables.tf, terraform.tfvars）]
  }

  [backend.tf\n（S3 State・DynamoDB Lock）]
}

[staging/\n（main.tf, variables.tf, terraform.tfvars）] --> [vpc/\n（VPC・サブネット・SG）]
[staging/\n（main.tf, variables.tf, terraform.tfvars）] --> [ecs/\n（ECS クラスター・サービス・タスク定義）]
[staging/\n（main.tf, variables.tf, terraform.tfvars）] --> [rds/\n（RDS インスタンス・SG）]
[staging/\n（main.tf, variables.tf, terraform.tfvars）] --> [alb/\n（ALB・リスナー・ターゲットグループ）]
[production/\n（main.tf, variables.tf, terraform.tfvars）] --> [vpc/\n（VPC・サブネット・SG）]
[production/\n（main.tf, variables.tf, terraform.tfvars）] --> [ecs/\n（ECS クラスター・サービス・タスク定義）]
[production/\n（main.tf, variables.tf, terraform.tfvars）] --> [rds/\n（RDS インスタンス・SG）]

@enduml
```

Terraform の運用方針：

| 原則 | 内容 |
| :--- | :--- |
| **State の S3 管理** | `terraform.tfstate` を S3 バケットで管理。DynamoDB でステートロック |
| **モジュール分割** | 再利用可能なコンポーネントはモジュール化。ステージング・本番で同一モジュールを使用 |
| **機密情報の管理** | `terraform.tfvars` に DB パスワード等を含めない。AWS Secrets Manager または環境変数で管理 |
| **Plan レビュー** | `terraform apply` 前に必ず `terraform plan` を実施し、変更内容をレビュー |
| **Drift 検出** | 定期的に `terraform plan` を実行し、コードと実環境の乖離を検出する |
