---
title: インフラアーキテクチャ
description: 国際貨物輸送管理システム（Go 版）のインフラアーキテクチャ設計。Docker/AWS ECS/RDS 構成、CI/CD パイプライン、監視設計を定義する。
published: true
date: 2026-07-10T00:00:00.000Z
tags: architecture, infrastructure, aws, ecs, docker, terraform, github-actions, go, golang
---

# インフラアーキテクチャ - 国際貨物輸送管理システム

## 概要

本ドキュメントでは、国際貨物輸送管理システムのインフラアーキテクチャを定義する。
コンテナ化された Go アプリケーションを **AWS ECS Fargate** 上で稼働させ、
**RDS PostgreSQL** でデータを永続化する。
**Terraform** によるインフラの IaC 管理と **GitHub Actions** による CI/CD パイプラインを整備する。
Go の静的リンクバイナリは JVM アプリケーションと比較してイメージサイズが大幅に小さく、起動が 1 秒未満と高速であるため、デプロイ・スケールアウトの俊敏性が高い。

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
      [ECS Fargate\ncargo-tracker-app\n(Go)]
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
[Application Load Balancer\n(ALB)] --> [ECS Fargate\ncargo-tracker-app\n(Go)] : HTTP (8080)
[ECS Fargate\ncargo-tracker-app\n(Go)] --> [RDS PostgreSQL 16\n(Multi-AZ)\nPrimary] : 5432
[ECS Fargate\ncargo-tracker-app\n(Go)] --> [Secrets Manager\n(DB 接続情報)]
[ECS Fargate\ncargo-tracker-app\n(Go)] --> [CloudWatch Logs\n(アプリログ)]
[RDS PostgreSQL 16\n(Multi-AZ)\nPrimary] --> [RDS PostgreSQL 16\n(Multi-AZ)\nStandby] : 同期レプリケーション
[NAT Gateway] --> [ECR\n(コンテナイメージ)] : イメージ Pull
[ECS Fargate\ncargo-tracker-app\n(Go)] --> [NAT Gateway] : 外部通信

developer --> [ECR\n(コンテナイメージ)] : docker push
developer --> [S3\n(Terraform State)] : terraform state

@enduml
```

## コンテナ化戦略

### Dockerfile 設計方針

マルチステージビルドを採用し、本番イメージのサイズを最小化する。
Go は静的リンクバイナリを生成できるため、実行ステージには **distroless/static** を採用し、
シェルやパッケージマネージャーを含まない最小・最小攻撃面のイメージ（数十 MB 程度）を実現する。

```plantuml
@startuml
title Dockerfile マルチステージビルド

package "Stage 1: Build" as build {
  [golang:1.24\n（ビルドステージ）] as builder
}

package "Stage 2: Runtime" as runtime {
  [gcr.io/distroless/static\n（実行ステージ）] as runner
}

note right of builder : go mod download で依存解決\nCGO_ENABLED=0 go build で\n静的リンクバイナリを生成
note right of runner : シェル・libc 不要の最小イメージ\nバイナリ 1 つのみコピー\n非 root（nonroot）ユーザーで実行

builder --> runner : COPY --from=build

@enduml
```

Dockerfile の設計原則：

| 原則 | 内容 |
| :--- | :--- |
| **マルチステージビルド** | ビルド依存関係（Go ツールチェーン）を本番イメージに含めない |
| **静的リンクバイナリ** | `CGO_ENABLED=0` でビルドし、`distroless/static` にバイナリのみ配置。JVM イメージ（数百 MB）に対し数十 MB に収まる |
| **非 root ユーザー** | `distroless/static:nonroot` を使用し、非 root ユーザーで実行 |
| **レイヤーキャッシュの最適化** | 依存関係（`go.mod` / `go.sum`）を先にコピーして `go mod download` し、ソースコードのコピーを後にする |
| **ヘルスチェック** | 自前実装の `/healthz` エンドポイントを ALB / ECS のヘルスチェックに使用（Spring Actuator 相当を軽量に実装） |
| **環境変数による設定** | DB 接続情報等は `ENV` / 実行時環境変数で注入。コードにハードコードしない |

### Docker Compose ローカル開発環境

```plantuml
@startuml
title Docker Compose ローカル開発環境

package "docker-compose.yml" as compose {

  package "app サービス" as app_service {
    [cargo-tracker-app\n(Go)\nport: 8080]
  }

  package "db サービス" as db_service {
    database "postgres:16-alpine\nport: 5432\nvolume: postgres_data"
  }

  package "開発支援サービス" as dev_services {
    [adminer\n(DB 管理 UI)\nport: 8081]
    [mailhog\n(メール確認)\nport: 8025]
  }
}

[cargo-tracker-app\n(Go)\nport: 8080] --> [postgres:16-alpine\nport: 5432\nvolume: postgres_data] : DATABASE_URL
[adminer\n(DB 管理 UI)\nport: 8081] --> [postgres:16-alpine\nport: 5432\nvolume: postgres_data]

note bottom of compose
  ローカル開発時から
  PostgreSQL を使用することで
  本番との差異を最小化する
  マイグレーション（golang-migrate 等）も
  ローカルで検証可能
end note

@enduml
```

## AWS 構成

### ECS Fargate 構成

Go アプリケーションはメモリフットプリントが小さく（常駐数十 MB 程度）、JVM のようなヒープ事前確保が不要なため、タスクサイズを小さく設定できる。

| 設定項目 | 値 | 説明 |
| :--- | :--- | :--- |
| クラスター名 | `cargo-tracker-cluster` | ECS クラスター |
| タスク定義 | `cargo-tracker-app` | Go コンテナ定義 |
| CPU | 256 (0.25 vCPU) | 初期設定。負荷に応じてスケール。`GOMAXPROCS` を vCPU 数に合わせて設定 |
| メモリ | 512 MB | Go はヒープ事前確保不要のため JVM 構成（1024 MB）から縮小 |
| 希望タスク数 | 2 | 最小稼働台数（高可用性） |
| 最大タスク数 | 6 | Auto Scaling 上限。起動 1 秒未満のためスケールアウトが高速 |
| サービスディスカバリ | ALB ターゲットグループ | `/healthz` によるヘルスチェック経由でルーティング |

Go ランタイム特性に関する補足：

| 項目 | 内容 |
| :--- | :--- |
| **GOMAXPROCS** | Fargate の CPU 制限と CPU 検出が乖離しないよう、`automaxprocs` または環境変数 `GOMAXPROCS` で vCPU 数に合わせる |
| **起動時間** | 1 秒未満。ヘルスチェックの `startPeriod` を短く設定でき、デプロイ・スケールアウトが高速 |
| **グレースフルシャットダウン** | SIGTERM を受信したら `http.Server.Shutdown` で処理中リクエストの完了を待って終了。ECS の `stopTimeout` 内（デフォルト 30 秒）に収める |

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
:Go 1.24 セットアップ\n(actions/setup-go);
fork
  :Lint\n(golangci-lint);
fork again
  :単体テスト\n(go test);
end fork
:統合テスト\n(go test + Testcontainers for Go);
:ビルド\n(go build);
:コードカバレッジレポート\n(go test -coverprofile);

if (すべてのチェックが通過?) then (yes)
  |CD ワークフロー (cd.yml)|
  :Docker イメージビルド;
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
| `ci.yml` | PR / push | golangci-lint・go test・go build・カバレッジ |
| `cd-staging.yml` | main push | ステージング環境への自動デプロイ |
| `cd-production.yml` | 手動承認 / release タグ | 本番環境へのデプロイ |
| `terraform-plan.yml` | infra/ 変更の PR | `terraform plan` 結果を PR にコメント |
| `terraform-apply.yml` | infra/ 変更の main push | `terraform apply` でインフラ更新 |

### デプロイ戦略

| 戦略 | 環境 | 内容 |
| :--- | :--- | :--- |
| **ローリングアップデート** | ステージング・本番 | ECS の `minimumHealthyPercent: 50`, `maximumPercent: 200` でゼロダウンタイムデプロイ。Go の高速起動により入れ替えが短時間で完了 |
| **ロールバック** | 本番 | 前バージョンの ECR イメージ SHA で `ecs update-service` を実行 |
| **Blue/Green** | 将来対応 | CodeDeploy + ALB の B/G デプロイ（高トラフィック時に検討） |

デプロイ時のグレースフルシャットダウン：

| 項目 | 内容 |
| :--- | :--- |
| **SIGTERM ハンドリング** | `signal.NotifyContext` で SIGTERM を捕捉し、新規リクエストの受付を停止 |
| **Shutdown** | `http.Server.Shutdown(ctx)` で処理中リクエストの完了を待機（タイムアウト付き） |
| **ECS 設定** | `stopTimeout`（デフォルト 30 秒）内に終了するよう Shutdown タイムアウトを 25 秒程度に設定 |

## 監視・ログ設計

```plantuml
@startuml
title 監視・ログアーキテクチャ

package "ECS Fargate (cargo-tracker-app)" as app {
  [Go アプリケーション\n(/healthz + slog 構造化ログ)]
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

[Go アプリケーション\n(/healthz + slog 構造化ログ)] --> [CloudWatch Logs\n(/ecs/cargo-tracker)] : awslogs ドライバー
[CloudWatch Logs\n(/ecs/cargo-tracker)] --> [CloudWatch Metrics\n(カスタムメトリクス)] : ログメトリクスフィルター
[CloudWatch Metrics\n(カスタムメトリクス)] --> [CloudWatch Alarms\n(アラート設定)] : メトリクス監視
[CloudWatch Alarms\n(アラート設定)] --> [SNS Topic] : アラーム発火
[SNS Topic] --> [Slack / メール通知]
[CloudWatch Metrics\n(カスタムメトリクス)] --> [CloudWatch Dashboard\n(可視化)]

note right of [CloudWatch Logs\n(/ecs/cargo-tracker)]
  ログレベル: INFO（本番）
  ログ形式: JSON 構造化ログ（log/slog）
  保持期間: 30 日
end note

@enduml
```

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

構造化ログの形式（JSON、`log/slog` で出力）：

```json
{
  "timestamp": "2026-07-10T00:00:00.000Z",
  "level": "INFO",
  "traceId": "abc123",
  "userId": "user-001",
  "context": "booking",
  "message": "貨物予約を登録しました",
  "bookingId": "BK-2026-0001"
}
```

## 環境構成

```plantuml
@startuml
title 環境構成

package "開発環境 (local)" as local {
  [GoLand / VS Code]
  database "Docker Compose\nPostgreSQL:5432"
  [Go アプリケーション\n(localhost:8080)]
}

package "CI 環境 (GitHub Actions)" as ci {
  [GitHub Actions Runner]
  database "Testcontainers for Go\n(PostgreSQL on-demand)"
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

[GoLand / VS Code] --> [Go アプリケーション\n(localhost:8080)]
[Go アプリケーション\n(localhost:8080)] --> [Docker Compose\nPostgreSQL:5432]

[GitHub Actions Runner] --> [テスト・ビルド実行]
[テスト・ビルド実行] --> [Testcontainers for Go\n(PostgreSQL on-demand)]

@enduml
```

### 環境別設定一覧

| 設定項目 | ローカル | ステージング | 本番 |
| :--- | :--- | :--- | :--- |
| DB | Docker PostgreSQL | RDS（Single-AZ） | RDS（Multi-AZ） |
| ECS タスク数 | - | 1 | 2〜6（Auto Scaling） |
| ログレベル | DEBUG | INFO | INFO |
| 環境識別（`APP_ENV`） | `local` | `staging` | `production` |
| DB マイグレーション | 自動（起動時） | 自動（起動時） | 自動（起動時） |
| マイグレーションの down / クリーン | 許可 | 禁止 | 禁止 |
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
