---
title: インフラストラクチャアーキテクチャ設計 - 国際貨物輸送管理システム
description: コンテナオーケストレーションに基づくインフラ設計
published: true
date: 2026-04-24T00:00:00.000Z
---

# インフラストラクチャアーキテクチャ設計

## 概要

マイクロサービスアーキテクチャを採用したバックエンドと React SPA フロントエンドを、コンテナベースのインフラ上で運用する。開発環境は Docker Compose、本番環境は AWS ECS（Fargate）を想定する。

## デプロイメントアーキテクチャ

### 全体構成

```plantuml
@startuml

title デプロイメントアーキテクチャ

cloud "インターネット" as internet

package "AWS" {
  package "VPC" {
    package "パブリックサブネット" {
      [ALB] as alb
    }

    package "プライベートサブネット" {
      package "ECS Cluster" {
        node "Gateway Service\n(gatewayms)" as gw
        node "Auth Service\n(authms)" as auths
        node "Booking Service" as bs
        node "Routing Service" as rs
        node "Tracking Service" as ts
        node "Handling Service" as hs
        node "Billing Service" as bis
        node "Frontend (Nginx)" as fe
      }

      package "データストア" {
        database "RDS (PostgreSQL)\nauth_db" as adb
        database "RDS (PostgreSQL)\nbooking_db" as bdb
        database "RDS (PostgreSQL)\nrouting_db" as rdb
        database "RDS (PostgreSQL)\ntracking_db" as tdb
        database "RDS (PostgreSQL)\nhandling_db" as hdb
        database "RDS (PostgreSQL)\nbilling_db" as bidb
        queue "Amazon MQ\n(RabbitMQ)" as mq
      }
    }
  }

  [ECR] as ecr
  [CloudWatch] as cw
  [S3] as s3
}

internet --> alb
alb --> fe
alb --> gw
gw --> bs
gw --> rs
gw --> ts
gw --> hs
gw --> bis
gw --> auths

auths --> adb
bs --> bdb
rs --> rdb
ts --> tdb
hs --> hdb
bis --> bidb

bs --> mq
hs --> mq
mq --> ts
mq --> bis

ecr --> gw
ecr --> auths
ecr --> bs
ecr --> rs
ecr --> ts
ecr --> hs
ecr --> bis
ecr --> fe

gw --> cw
auths --> cw
bs --> cw
rs --> cw
ts --> cw
hs --> cw
bis --> cw

@enduml
```

## 環境構成

### 環境一覧

| 環境 | 用途 | インフラ | デプロイ方式 |
| :--- | :--- | :--- | :--- |
| ローカル開発 | 開発者の PC | Docker Compose | docker-compose up |
| 開発 | 結合テスト | AWS ECS (Fargate) | CI/CD 自動デプロイ |
| ステージング | 受入テスト | AWS ECS (Fargate) | CI/CD 自動デプロイ |
| 本番 | 商用運用 | AWS ECS (Fargate) | CI/CD + 承認ゲート |

### ローカル開発環境（Docker Compose）

```plantuml
@startuml

title ローカル開発環境

package "Docker Compose" {
  node "gateway-service\n:8080" as gw
  node "auth-service\n:8081" as auths
  node "booking-service\n:8082" as bs
  node "routing-service\n:8083" as rs
  node "tracking-service\n:8084" as ts
  node "handling-service\n:8085" as hs
  node "billing-service\n:8086" as bis
  node "frontend\n:3000" as fe
  database "postgresql\n:5432" as db
  queue "rabbitmq\n:5672/15672" as mq
}

fe --> gw : localhost:8080
gw --> auths : /api/auth/**
gw --> bs : /api/booking/**
gw --> rs : /api/routing/**
gw --> ts : /api/tracking/**
gw --> hs : /api/handling/**
gw --> bis : /api/billing/**

auths --> db
bs --> db
rs --> db
ts --> db
hs --> db
bis --> db

bs --> mq
hs --> mq
mq --> ts
mq --> bis

@enduml
```

## ネットワーク設計

### VPC 設計

| サブネット | CIDR | 用途 |
| :--- | :--- | :--- |
| パブリック A | 10.0.1.0/24 | ALB, NAT Gateway |
| パブリック B | 10.0.2.0/24 | ALB（冗長化） |
| プライベート A | 10.0.11.0/24 | ECS タスク |
| プライベート B | 10.0.12.0/24 | ECS タスク（冗長化） |
| プライベート C | 10.0.21.0/24 | RDS, Amazon MQ |
| プライベート D | 10.0.22.0/24 | RDS（冗長化） |

### セキュリティグループ

| セキュリティグループ | インバウンド | 用途 |
| :--- | :--- | :--- |
| alb-sg | 80, 443 from 0.0.0.0/0 | ALB |
| ecs-sg | 8080-8086 from alb-sg | ECS タスク |
| rds-sg | 5432 from ecs-sg | RDS |
| mq-sg | 5672 from ecs-sg | Amazon MQ |

## コンテナ設計

### Dockerfile（バックエンドサービス共通）

```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Dockerfile（フロントエンド）

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### コンテナリソース設定

| サービス | CPU | メモリ | 最小タスク数 | 最大タスク数 |
| :--- | :--- | :--- | :--- | :--- |
| Auth Service | 256 | 512 MB | 2 | 4 |
| Booking Service | 512 | 1024 MB | 2 | 4 |
| Routing Service | 256 | 512 MB | 2 | 4 |
| Tracking Service | 512 | 1024 MB | 2 | 6 |
| Handling Service | 512 | 1024 MB | 2 | 4 |
| Billing Service | 256 | 512 MB | 1 | 2 |
| Frontend | 256 | 512 MB | 2 | 4 |
| API Gateway | 256 | 512 MB | 2 | 4 |

## CI/CD パイプライン

```plantuml
@startuml

title CI/CD パイプライン

start

:コード変更;
:Git Push;

fork
  :ユニットテスト;
fork again
  :静的解析 (SonarQube);
fork again
  :セキュリティスキャン;
end fork

:Gradle ビルド;

if (テスト成功?) then (yes)
  :Docker イメージビルド;
  :ECR にプッシュ;

  if (ブランチ) then (main)
    :開発環境デプロイ;
    :統合テスト;
    if (統合テスト成功?) then (yes)
      :ステージングデプロイ;
      :受入テスト;
      if (本番デプロイ?) then (yes)
        :承認待ち;
        :本番デプロイ（Blue/Green）;
      endif
    endif
  else (feature)
    :開発環境デプロイ（PR 環境）;
  endif

  :スモークテスト;
else (no)
  :通知（失敗）;
  stop
endif

:デプロイ完了;
stop

@enduml
```

### CI/CD ツール

| カテゴリ | ツール | 用途 |
| :--- | :--- | :--- |
| CI | GitHub Actions | ビルド・テスト・静的解析 |
| CD | GitHub Actions + AWS CDK | デプロイ自動化 |
| コンテナレジストリ | Amazon ECR | Docker イメージ管理 |
| IaC | Terraform | インフラプロビジョニング |
| 品質ゲート | SonarQube | コード品質・カバレッジ |

## デプロイメント戦略

### Blue/Green デプロイメント（本番環境）

```plantuml
@startuml

title Blue/Green デプロイメント

cloud "ALB" as alb

package "Blue 環境（現行）" {
  node "Gateway v1.0" as g1
  node "Auth v1.0" as a1
  node "Booking v1.0" as b1
  node "Routing v1.0" as r1
  node "Tracking v1.0" as t1
  node "Handling v1.0" as h1
  node "Billing v1.0" as bi1
}

package "Green 環境（新規）" {
  node "Gateway v1.1" as g2
  node "Auth v1.1" as a2
  node "Booking v1.1" as b2
  node "Routing v1.1" as r2
  node "Tracking v1.1" as t2
  node "Handling v1.1" as h2
  node "Billing v1.1" as bi2
}

alb --> g1 : 100% (切替前)
alb ..> g2 : 0% → 100% (切替後)

@enduml
```

- 本番環境: Blue/Green デプロイメントで即座にロールバック可能
- 開発/ステージング: ローリングアップデートで迅速にデプロイ

## 監視・ログ管理

### 監視対象

| カテゴリ | 監視項目 | ツール | アラート閾値 |
| :--- | :--- | :--- | :--- |
| インフラ | CPU/メモリ使用率 | CloudWatch | CPU > 80% |
| アプリケーション | レスポンスタイム | CloudWatch | p99 > 3s |
| アプリケーション | エラー率 | CloudWatch | > 1% |
| データベース | 接続数 | CloudWatch | > 80% |
| メッセージング | キュー深度 | CloudWatch | > 1000 |

### ログ管理

| ログ種別 | 保存先 | 保持期間 |
| :--- | :--- | :--- |
| アプリケーションログ | CloudWatch Logs | 30 日 |
| アクセスログ | CloudWatch Logs | 90 日 |
| 監査ログ | S3 | 1 年 |

## バックアップ・災害復旧

### バックアップ戦略

| 対象 | 方式 | 頻度 | 保持期間 |
| :--- | :--- | :--- | :--- |
| RDS | 自動スナップショット | 日次 | 7 日 |
| RDS | 手動スナップショット | リリース前 | 30 日 |

### RPO/RTO

| 指標 | 目標値 |
| :--- | :--- |
| RPO | 1 時間以内 |
| RTO | 4 時間以内 |

## セキュリティ

### 多層防御

| 層 | 対策 |
| :--- | :--- |
| ネットワーク | VPC, セキュリティグループ, プライベートサブネット |
| 通信 | TLS/SSL (ACM), HTTPS 強制 |
| 認証・認可 | Spring Security + JWT |
| データ保護 | RDS 暗号化（AES-256）、S3 暗号化 |
| アプリケーション | 入力検証、OWASP 対策 |

## コスト見積（月額概算）

| リソース | スペック | 月額概算 |
| :--- | :--- | :--- |
| ECS Fargate | 8 サービス × 2 タスク（gateway/auth/booking/routing/tracking/handling/billing/frontend） | $280 |
| RDS PostgreSQL | db.t3.medium × 6 | $600 |
| Amazon MQ | mq.m5.large | $200 |
| ALB | 1 台 | $30 |
| CloudWatch | ログ・メトリクス | $50 |
| ECR | イメージストレージ | $10 |
| **合計** | | **$1,170** |

## 参照

- [バックエンドアーキテクチャ設計](architecture_backend.md)
- [フロントエンドアーキテクチャ設計](architecture_frontend.md)
- [アーキテクチャ設計ガイド](../reference/アーキテクチャ設計ガイド.md)
