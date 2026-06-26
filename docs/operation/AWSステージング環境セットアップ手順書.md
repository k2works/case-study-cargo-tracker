# AWS ステージング環境セットアップ手順書

## 概要

Terraform を使用して AWS 上に **国際貨物輸送管理システム (Cargo Tracker) Haskell 版** のステージング環境を構築するための手順を説明します。

Infrastructure as Code (IaC) により、インフラストラクチャの一貫性、再現性、バージョン管理を保証します。

| サービス | 略称 | コンテナイメージ | ポート | 説明 |
|---------|------|----------------|--------|------|
| cargo-tracker | cargo | cargo-tracker | 8080 | Servant + Warp (Haskell) の貨物輸送管理アプリケーション |

---

## アーキテクチャ

```plantuml
@startuml

title ステージング環境構成図

cloud "AWS Cloud" as aws {

  node "Route 53" as route53 {
    component "DNS" as dns
  }

  rectangle "VPC" as vpc {

    rectangle "Public Subnet" as pub_subnet {
      component "NAT Gateway" as natgw
      component "ALB" as alb
    }

    rectangle "Private Subnet" as priv_subnet {
      component "ECS Task\ncargo-tracker (8080)\n/*" as ecs_svc
      database "RDS\n(PostgreSQL 16)" as rds
    }
  }

  node "ECR" as ecr {
    artifact "cargo-tracker" as img
  }

  node "Systems Manager" as ssm {
    component "Parameter Store" as params
    component "Application Manager" as appmgr
  }

  node "AWS Backup" as backup {
    component "Backup Vault" as vault
  }

  node "S3" as s3 {
    component "Terraform State" as tfstate
  }

  node "DynamoDB" as dynamo {
    component "State Lock" as tflock
  }
}

actor "ユーザー" as user
actor "開発者" as dev

user --> dns : HTTPS
dns --> alb : HTTPS
alb --> ecs_svc : "/*"
ecs_svc --> rds : postgresql-simple (5432)
priv_subnet --> natgw : アウトバウンド
ecr --> ecs_svc : イメージプル
params --> ecs_svc : 設定値
vault --> rds : スナップショット
dev --> tfstate : terraform apply
dev --> tflock : 状態ロック

@enduml
```

### AWS サービス構成

| サービス | 用途 |
|---------|------|
| Route 53 | DNS 管理・カスタムドメイン (`stg.cargo-tracker.example.com`) |
| ECS (Fargate) | Haskell バイナリコンテナの実行 (CPU 256 / Memory 512 で開始) |
| ALB | ECS タスクへのトラフィック分散・TLS 終端 |
| ECR | Docker イメージレジストリ |
| RDS (PostgreSQL 16) | データベース (Single-AZ、ステージング) |
| VPC | ネットワーク分離 (パブリック / プライベートサブネット、Multi-AZ) |
| NAT Gateway | プライベートサブネットからのアウトバウンド通信 |
| Systems Manager | パラメータストア (DB 接続情報・JWT 鍵) |
| CloudWatch Logs | ECS タスクのコンテナログ (katip JSON 構造化ログ) |
| S3 | Terraform 状態ファイル管理 |
| DynamoDB | Terraform 状態ロック |
| Resource Groups | Application Manager 用リソースグループ (タグベース) |
| AWS Backup | RDS スナップショットの自動取得 |

---

## 前提条件

- AWS アカウント (適切な IAM 権限)
- パッケージマネージャー
  - Windows: [Scoop](https://scoop.sh/)
  - macOS: [Homebrew](https://brew.sh/)
- Terraform >= 1.0.0, < 2.0.0
- AWS CLI v2
- aws-vault (開発環境での認証管理)
- Node.js >= 22 LTS / npm (Gulp タスクランナー実行用)
- Docker Desktop (LocalStack テスト用)
- Go >= 1.21 (Terratest 実行時)
- Git
- GHC 9.10 + Stack (Haskell ビルド用、開発 PC のみ)

---

## インストール

### 1. AWS CLI のセットアップ

#### 1.1 AWS CLI のインストール

**Windows (Scoop)**:

```bash
scoop install aws
```

**macOS (Homebrew)**:

```bash
brew install awscli
```

インストール確認:

```bash
aws --version
```

#### 1.2 aws-vault のインストール

aws-vault は AWS の認証情報を OS のキーチェーンに安全に保存し、一時的なセッション認証情報を自動生成するツールです。

**Windows (Scoop)**:

```bash
scoop install aws-vault
```

**macOS (Homebrew)**:

```bash
brew install aws-vault
```

インストール確認:

```bash
aws-vault --version
```

#### 1.3 開発環境の認証設定 (aws-vault 使用)

1. マネジメントコンソールからアクセスキーを作成します
2. aws-vault にプロファイルを登録します

```bash
aws-vault add cargo-tracker-stg
```

3. `~/.aws/config` にプロファイルのリージョンを設定します

```text
[profile cargo-tracker-stg]
region=ap-northeast-1
```

> **重要**: リージョンが設定されていないと `aws-vault exec` 実行時にエラーが発生します。

4. 登録後、AWS リソースへのアクセスを確認します

```bash
aws-vault exec cargo-tracker-stg -- aws s3 ls
```

5. `~/.aws/credentials` に以下を追加します

```text
[cargo-tracker-stg]
credential_process=aws-vault exec cargo-tracker-stg --json --prompt=osascript
region=ap-northeast-1
output=json
```

> **補足**: Windows の場合は `--prompt=wincredui` に変更してください。

#### 1.4 手動で実行する場合

手動で Terraform や AWS CLI コマンドを実行する場合は、必ず `aws-vault exec` 経由で実行してください。

```bash
# Terraform の初期化
aws-vault exec cargo-tracker-stg -- terraform init --backend-config=backend.hcl

# Terraform の plan / apply
aws-vault exec cargo-tracker-stg -- terraform plan
aws-vault exec cargo-tracker-stg -- terraform apply

# AWS CLI コマンド
aws-vault exec cargo-tracker-stg -- aws s3 ls
```

#### 1.5 マネジメントコンソールにログイン

```bash
aws-vault login cargo-tracker-stg
```

---

## 設定

### 2. Terraform ディレクトリ構成

Terraform コードは `ops/terraform/` 配下に以下の構成で配置します。

```text
ops/terraform/
├── live/
│   ├── global/
│   │   ├── variables/          # プロジェクト共通変数
│   │   ├── s3/                 # Terraform 状態管理用 S3 バケット
│   │   └── iam/                # OIDC 認証用 IAM ロール
│   ├── stage/
│   │   ├── ssm/
│   │   │   ├── paramstore/     # SSM パラメータストア (DB / JWT)
│   │   │   └── appmanager/     # Application Manager リソースグループ
│   │   ├── vpc/                # VPC・サブネット・NAT Gateway
│   │   ├── data-stores/
│   │   │   └── rds/            # RDS PostgreSQL 16
│   │   ├── backup/             # AWS Backup
│   │   ├── repository/
│   │   │   └── ecr/cargo-tracker  # ECR リポジトリ
│   │   ├── services/
│   │   │   └── ecs/            # ECS (Fargate + ALB)
│   │   └── variables/          # ステージ変数
│   └── mgmt/
│       └── stage/
│           └── bastion/        # 踏み台サーバー
├── modules/
│   ├── iam/
│   │   └── ecs/                # ECS タスクロール / タスク実行ロール
│   ├── networking/
│   │   └── vpc/                # VPC モジュール
│   ├── data-stores/
│   │   └── rds/                # RDS モジュール
│   ├── backup/                 # AWS Backup モジュール
│   ├── repository/
│   │   └── ecr/                # ECR モジュール
│   └── services/
│       └── ecs/                # ECS モジュール (クラスター・ALB・サービス)
└── test/
    ├── unit/                   # 単体テスト
    └── integration/            # 結合テスト
```

### 3. Terraform 状態管理用 S3 バケットの作成

Terraform の状態ファイル (`.tfstate`) を S3 に保存し、DynamoDB テーブルで状態ロック (同時実行防止) を行います。

#### 3.1 リソース定義

```hcl
resource "aws_s3_bucket" "terraform_state" {
  bucket = "${local.project_name}-staging-terraform-state"

  lifecycle {
    prevent_destroy = false
  }
}

resource "aws_s3_bucket_versioning" "enabled" {
  bucket = aws_s3_bucket.terraform_state.bucket
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "public_access" {
  bucket                  = aws_s3_bucket.terraform_state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "terraform_locks" {
  name         = "${local.project_name}-staging-terraform-state-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
```

#### 3.2 プロビジョニング手順

作業ディレクトリ: `ops/terraform/live/global/s3`

```bash
terraform init
terraform plan
terraform apply
```

> **重要**: S3 バケットと DynamoDB テーブルは Terraform 状態の保存先です。他のすべてのリソースより先に作成してください。

### 4. GitHub Actions 用 IAM ロールの作成

作業ディレクトリ: `ops/terraform/live/global/iam`

OIDC プロバイダーと GitHub Actions 用の IAM ロールを作成します。

aws-vault 使用時は `--no-session` が必要です (STS 一時認証情報では IAM API が制限されるため)。

```bash
aws-vault exec cargo-tracker-stg --no-session -- terraform init
aws-vault exec cargo-tracker-stg --no-session -- terraform plan
aws-vault exec cargo-tracker-stg --no-session -- terraform apply
```

作成後:

1. 出力された IAM ロールの ARN をコピー
2. GitHub Actions の `AWS_ROLE_ARN` シークレットに設定 (`.github/workflows/cd-staging.yml` で使用)

---

## タスクランナーによる自動化

Terraform プロビジョニング / 廃棄作業は Gulp タスクランナーで自動化できます。

| 変数 | 説明 | 例 |
|------|------|----|
| `STG_AWS_PROFILE` | aws-vault で使用するプロファイル名 | `cargo-tracker-stg` |

```bash
# 代表的なコマンド
npm run stg:setup                    # 初回セットアップ (全リソース一括)
npm run stg:provision:all            # 全リソースの一括プロビジョニング
npm run stg:plan:all                 # 全リソースの plan のみ
npm run stg:destroy:all              # 全リソースの一括廃棄
npx gulp --tasks | grep stg          # ヘルプ
```

---

## プロビジョニング

### プロビジョニングフロー

```plantuml
@startuml

title ステージング環境プロビジョニングフロー

|グローバル設定|
start
:S3 バケット作成\n(状態管理);
:IAM ロール作成\n(OIDC 認証);

|アプリケーション基盤|
:SSM パラメータストア設定\n(DB 接続情報・JWT 鍵);
:VPC 設定;
:RDS 設定 (PostgreSQL 16);
:AWS Backup 設定\n(RDS);

|リポジトリ・ビルド|
:ECR リポジトリ作成;
:Haskell Docker イメージ\nビルド & ECR プッシュ;

|サービス設定|
:ECS 設定\n(Fargate + ALB);
note right: ALB ルーティング\n(パスベース)

:Application Manager\n(リソースグループ);

|管理サーバー|
:EC2 キーペア作成;
note right: **任意**\n踏み台サーバー用

:踏み台サーバー\nセットアップ;
note right: **任意**\nRDS 接続・バックアップ用

|ネットワーク設定|
:Route 53 設定;
:カスタムドメイン設定\n(stg.cargo-tracker.example.com);

stop

@enduml
```

### 5. SSM パラメータストアの設定

AWS Systems Manager Parameter Store を使用して、RDS の認証情報 (ユーザー名・パスワード) と JWT 鍵を `SecureString` として暗号化管理します。

#### 5.1 リソース定義

```hcl
resource "aws_ssm_parameter" "db_username" {
  name  = "${local.ssm_parameter_key}/DB_USERNAME"
  type  = "SecureString"
  value = var.db_username

  tags = {
    Name              = local.resource_name
    ResourceGroupName = local.resource_name
  }
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.ssm_parameter_key}/DB_PASSWORD"
  type  = "SecureString"
  value = var.db_password

  tags = {
    Name              = local.resource_name
    ResourceGroupName = local.resource_name
  }
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "${local.ssm_parameter_key}/JWT_SECRET"
  type  = "SecureString"
  value = var.jwt_secret
}
```

#### 5.2 プロビジョニング手順

1. `secret.tfvars` ファイルを作成します

```text
db_username = "cargo_tracker"
db_password = "<強力なパスワード>"
jwt_secret  = "<64 文字以上のランダム文字列>"
```

> **重要**: `secret.tfvars` は Git 管理外にしてください (`.gitignore` に追加済み)。

2. Terraform を実行します

作業ディレクトリ: `ops/terraform/live/stage/ssm/paramstore`

```bash
terraform init --backend-config=backend.hcl
terraform plan --var-file=secret.tfvars
terraform apply --var-file=secret.tfvars
```

### 6. VPC の設定

パブリックサブネット (ALB・NAT Gateway・踏み台サーバー) とプライベートサブネット (ECS タスク・RDS) を複数のアベイラビリティゾーンに分散配置し、高可用性とセキュリティを確保します。

#### 6.1 ネットワーク構成

```plantuml
@startuml

title VPC ネットワーク構成図

rectangle "VPC (10.0.0.0/16)" as vpc {

  rectangle "Public Subnet AZ-1a\n10.0.1.0/24" as pub1a {
    component "NAT Gateway" as nat
    component "ALB" as alb
  }

  rectangle "Public Subnet AZ-1c\n10.0.2.0/24" as pub1c {
  }

  rectangle "Private Subnet AZ-1a\n10.0.10.0/24" as priv1a {
    component "ECS Tasks" as ecs
    database "RDS Primary" as rds
  }

  rectangle "Private Subnet AZ-1c\n10.0.11.0/24" as priv1c {
    component "RDS Standby" as rds_sb
  }
}

cloud "Internet" as inet

inet --> alb : HTTPS (443)
alb --> ecs : 8080
ecs --> rds : 5432
priv1a --> nat : アウトバウンド
nat --> inet

@enduml
```

#### 6.2 サブネット設定

| サブネット | CIDR | AZ | 用途 |
|-----------|------|----|------|
| Public AZ-1a | `10.0.1.0/24` | ap-northeast-1a | ALB、NAT Gateway、踏み台サーバー |
| Public AZ-1c | `10.0.2.0/24` | ap-northeast-1c | ALB (マルチ AZ) |
| Private AZ-1a | `10.0.10.0/24` | ap-northeast-1a | ECS タスク、RDS プライマリ |
| Private AZ-1c | `10.0.11.0/24` | ap-northeast-1c | RDS スタンバイ (マルチ AZ、将来) |

#### 6.3 主要リソース定義

```hcl
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr  # 10.0.0.0/16
  instance_tenancy     = "default"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name              = var.tags_name
    ResourceGroupName = var.tags_name
  }
}
```

**NAT Gateway (プライベートサブネットのアウトバウンド通信用):**

```hcl
resource "aws_eip" "nat" {
  count  = var.nat_gw_enable ? 1 : 0
  domain = "vpc"
}

resource "aws_nat_gateway" "main" {
  count         = var.nat_gw_enable ? 1 : 0
  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public_1a.id
}
```

#### 6.4 プロビジョニング手順

作業ディレクトリ: `ops/terraform/live/stage/vpc`

```bash
terraform init --backend-config=backend.hcl
terraform plan
terraform apply
```

### 7. RDS の設定

作業ディレクトリ: `ops/terraform/live/stage/data-stores/rds`

```bash
terraform init --backend-config=backend.hcl
terraform plan
terraform apply
```

#### 7.1 RDS 設定パラメータ

| パラメータ | ステージング値 | 説明 |
|-----------|-------------|------|
| `instance_class` | `db.t3.medium` | インスタンスタイプ |
| `allocated_storage` | `50 GB` | ストレージ容量 |
| `engine` / `engine_version` | `postgres` / `16.4` | PostgreSQL 16 |
| `backup_retention_period` | `7 日` | 自動バックアップ保持日数 |
| `multi_az` | `false` | ステージングは Single-AZ |
| `enable_blue_green_update` | `true` | Blue/Green Deployment 有効 |
| `skip_final_snapshot` | `false` | 削除時に最終スナップショットを取得 |
| `deletion_protection` | `false` | 削除保護 (ステージングは無効) |
| `apply_immediately` | `true` | 変更を即時適用 |
| `parameter_group_family` | `postgres16` | パラメータグループ |

#### 7.2 Blue/Green Deployment

RDS の Blue/Green Deployment は、エンジンバージョンアップグレードやパラメータグループ変更をダウンタイム数十秒で実行する仕組みです。

```plantuml
@startuml

title RDS Blue/Green Deployment フロー

start

:terraform apply\n(engine_version 変更等);

:blue_green_update { enabled = true } を検出;

partition "AWS 内部で自動実行" {
  :グリーン環境を作成;
  note right: 論理レプリケーションで\nデータを同期
  :データ同期完了を待機;
  :Switchover 実行;
  note right: ダウンタイム: 数十秒
  :旧インスタンス (ブルー) 削除;
}

:Terraform State 自動更新;

stop

@enduml
```

### 8. AWS Backup (RDS)

AWS Backup により RDS のスナップショットを日次・週次で自動取得します。`Backup = "true"` タグが付与された RDS インスタンスが対象です。

#### 8.1 バックアップポリシー

| ルール | スケジュール | 保持期間 |
|--------|-------------|---------|
| daily | 毎日 JST 0:00 (UTC 15:00) | 7 日 |
| weekly | 毎週日曜 JST 0:00 (UTC 15:00) | 30 日 |

#### 8.2 主要リソース定義

```hcl
resource "aws_backup_vault" "main" {
  name = "${var.app_env_name}-backup-vault"
}

resource "aws_backup_plan" "main" {
  name = "${var.app_env_name}-backup-plan"

  rule {
    rule_name         = "daily"
    target_vault_name = aws_backup_vault.main.name
    schedule          = "cron(0 15 * * ? *)"   # JST 0:00
    start_window      = 60
    completion_window = 180
    lifecycle {
      delete_after = var.daily_retention_days
    }
  }

  rule {
    rule_name         = "weekly"
    target_vault_name = aws_backup_vault.main.name
    schedule          = "cron(0 15 ? * SUN *)"
    start_window      = 60
    completion_window = 180
    lifecycle {
      delete_after = var.weekly_retention_days
    }
  }
}

resource "aws_backup_selection" "tagged" {
  name         = "${var.app_env_name}-backup-selection"
  iam_role_arn = aws_iam_role.backup.arn
  plan_id      = aws_backup_plan.main.id

  selection_tag {
    type  = "STRINGEQUALS"
    key   = "Backup"
    value = "true"
  }
}
```

#### 8.3 プロビジョニング手順

作業ディレクトリ: `ops/terraform/live/stage/backup`

```bash
terraform init --backend-config=backend.hcl
terraform plan
terraform apply
```

### 9. ECR リポジトリの設定

Cargo Tracker サービスの Docker イメージリポジトリを ECR に作成します。

#### 9.1 リソース定義

```hcl
resource "aws_ecr_repository" "app" {
  name = "cargo-tracker"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
```

#### 9.2 プロビジョニング手順

作業ディレクトリ: `ops/terraform/live/stage/repository/ecr/cargo-tracker`

```bash
terraform init --backend-config=backend.hcl
terraform plan
terraform apply
```

### 10. Docker イメージ ビルド & ECR プッシュ

ECR リポジトリ作成後、ECS でサービスを作成する前に、Cargo Tracker の Haskell Docker イメージをビルドして ECR にプッシュします。

#### 10.1 タスクランナー (推奨)

```bash
npm run docker:build                # Stack build + Docker build
npm run stg:ecr:push                # ECR ログイン & プッシュ
```

#### 10.2 手動実行

```bash
# 1. ECR ログイン
aws-vault exec cargo-tracker-stg -- aws ecr get-login-password --region ap-northeast-1 | \
  docker login --username AWS --password-stdin <アカウント ID>.dkr.ecr.ap-northeast-1.amazonaws.com

# 2. イメージをビルド & プッシュ (マルチステージビルド: stack build → debian-slim)
docker build -t <アカウント ID>.dkr.ecr.ap-northeast-1.amazonaws.com/cargo-tracker:latest apps/cargo-tracker
docker push <アカウント ID>.dkr.ecr.ap-northeast-1.amazonaws.com/cargo-tracker:latest

# 3. SHA タグでもプッシュ (デプロイの追跡性)
GIT_SHA=$(git rev-parse --short HEAD)
docker tag <アカウント ID>.dkr.ecr.ap-northeast-1.amazonaws.com/cargo-tracker:latest \
  <アカウント ID>.dkr.ecr.ap-northeast-1.amazonaws.com/cargo-tracker:$GIT_SHA
docker push <アカウント ID>.dkr.ecr.ap-northeast-1.amazonaws.com/cargo-tracker:$GIT_SHA
```

### 11. ECS の設定

ECS (Elastic Container Service) + Fargate を使用したデプロイ構成です。ALB (Application Load Balancer) を前段に配置し、トラフィックを ECS タスクに振り分けます。

#### 11.1 ECS の全体構成

```plantuml
@startuml

title ECS + ALB 構成図

rectangle "VPC" as vpc {

  rectangle "Public Subnet" as pub {
    component "ALB (HTTPS 443)" as alb
  }

  rectangle "Private Subnet" as priv {
    component "ECS Task\ncargo-tracker (8080)" as ecs_svc
    database "RDS\n(PostgreSQL 16)" as rds
  }
}

node "ECR" as ecr

alb --> ecs_svc : Target Group (HTTP 8080)
ecs_svc --> rds : 5432 (postgresql-simple)
ecr --> ecs_svc : Pull Image

@enduml
```

主要コンポーネント:

| コンポーネント | 説明 |
|-------------|------|
| ECS クラスター | Fargate 起動タイプ |
| タスク定義 | Haskell コンテナイメージ、CPU 256 / Memory 512、環境変数、ログ設定 |
| ECS サービス | タスクの希望数 1-2 を維持、Auto Scaling 設定 (CPU 80%) |
| ALB | パブリックサブネットに配置、HTTPS 終端 (ACM 証明書) |
| ターゲットグループ | ヘルスチェックパス `/health` |
| セキュリティグループ | ALB 用 (443 許可) と ECS タスク用 (ALB からの 8080 のみ許可) |

#### 11.2 環境変数

各 ECS タスクに設定する環境変数 (Haskell アプリケーション用):

| 環境変数 | 説明 | 例 |
|---------|------|-----|
| `APP_ENV` | アプリケーション環境 | `staging` |
| `PORT` | コンテナポート番号 | `8080` |
| `DATABASE_URL` | PostgreSQL 接続文字列 | `postgres://cargo_tracker:<password>@<rds-host>:5432/cargo_tracker_staging?sslmode=require` |
| `JWT_SECRET` | JWT 署名鍵 (SecureString) | (SSM から) |
| `LOG_LEVEL` | ログレベル | `INFO` |
| `SMTP_HOST` / `SMTP_PORT` | メール送信先 | (将来) |

#### 11.3 IAM ロールの設定

ECS タスクには 2 種類の IAM ロールが必要です。

**タスクロール** — タスク内のコンテナが AWS リソースにアクセスする際に使用:

```hcl
resource "aws_iam_role" "task_role" {
  name               = "${var.resource_name}-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}
```

**タスク実行ロール** — ECR イメージ取得、CloudWatch Logs 書き込み、SSM パラメータ取得に必要:

```hcl
resource "aws_iam_role" "task_exec_role" {
  name               = "${var.resource_name}-task-exec-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}
```

タスク実行ロールに必要な権限:

| 権限 | 説明 |
|------|------|
| `ecr:GetAuthorizationToken`, `ecr:BatchGetImage` 等 | ECR からのイメージプル |
| `logs:CreateLogStream`, `logs:PutLogEvents` | CloudWatch Logs への書き込み |
| `ssm:GetParameters` | SSM パラメータの取得 (`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`) |
| `kms:Decrypt` | SecureString パラメータの復号 |

#### 11.4 セキュリティグループの設定

**ALB 用** — 外部からの HTTPS アクセスを許可:

```hcl
resource "aws_security_group" "alb" {
  name   = "${var.resource_name}-alb-sg"
  vpc_id = var.vpc_id
}

# HTTPS (443) のインバウンドルール、HTTP (80) → 443 リダイレクト
# 全アウトバウンドを許可
```

**ECS タスク用** — ALB からのトラフィックのみ許可:

```hcl
resource "aws_security_group" "ecs_tasks" {
  name   = "${var.resource_name}-ecs-tasks-sg"
  vpc_id = var.vpc_id
}

# ポート 8080 に対して ALB セキュリティグループからのインバウンドを許可
# 全アウトバウンドを許可 (RDS 5432、NAT 経由の外部 API)
```

#### 11.5 ALB (Application Load Balancer) の設定

```hcl
# ALB 本体
resource "aws_lb" "alb" {
  name               = "${var.resource_name}-alb"
  internal           = false
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]
}

# ターゲットグループ
resource "aws_alb_target_group" "cargo_tracker" {
  name        = "${var.resource_name}-cargo-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"  # Fargate では ip を指定

  health_check {
    path                = "/health"
    protocol            = "HTTP"
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }
}

# HTTPS リスナー (ACM 証明書を ALB にアタッチ)
resource "aws_alb_listener" "https" {
  load_balancer_arn = aws_lb.alb.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_alb_target_group.cargo_tracker.arn
  }
}

# HTTP → HTTPS リダイレクト
resource "aws_alb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.alb.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}
```

> **注意**: ALB やターゲットグループには **32 文字以内・英数字とハイフンのみ** という命名制約があります。

#### 11.6 ECS クラスターとタスク定義

```hcl
resource "aws_ecs_cluster" "cluster" {
  name = "cargo-tracker-staging-cluster"
}

resource "aws_ecs_task_definition" "cargo_tracker" {
  family                   = "cargo-tracker-staging-task"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  task_role_arn            = var.task_role_arn
  execution_role_arn       = var.task_exec_role_arn

  container_definitions = jsonencode([
    {
      name      = "cargo-tracker"
      image     = "${var.ecr_repo_url}:latest"
      cpu       = 256
      memory    = 512
      essential = true
      portMappings = [
        { containerPort = 8080, hostPort = 8080 }
      ]
      environment = [
        { name = "APP_ENV",   value = "staging" },
        { name = "PORT",      value = "8080" },
        { name = "LOG_LEVEL", value = "INFO" }
      ]
      secrets = [
        { name = "DATABASE_URL", valueFrom = var.ssm_database_url_arn },
        { name = "JWT_SECRET",   valueFrom = var.ssm_jwt_secret_arn }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs.name
          awslogs-region        = "ap-northeast-1"
          awslogs-stream-prefix = "cargo-tracker"
        }
      }
      healthCheck = {
        command  = ["CMD-SHELL", "wget -q --spider http://localhost:8080/health || exit 1"]
        interval = 30
        timeout  = 5
        retries  = 3
        startPeriod = 60
      }
    }
  ])
}
```

> **Haskell ランタイム特性**: GHC は JVM 比でメモリフットプリントが小さい。256 CPU / 512 MB で開始し、負荷に応じて拡大する。

#### 11.7 ECS サービスの作成

```hcl
resource "aws_ecs_service" "cargo_tracker" {
  name            = "cargo-tracker-staging-svc"
  cluster         = aws_ecs_cluster.cluster.id
  task_definition = aws_ecs_task_definition.cargo_tracker.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_alb_target_group.cargo_tracker.arn
    container_name   = "cargo-tracker"
    container_port   = 8080
  }

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200
}
```

#### 11.8 プロビジョニング手順

作業ディレクトリ: `ops/terraform/live/stage/services/ecs/`

```bash
terraform init --backend-config=backend.hcl
terraform plan
terraform apply
```

#### 11.9 デプロイ手順

ECS デプロイでは、ECR にプッシュ済みの最新イメージを使用してローリングデプロイを実行します。

```bash
# タスクランナー (推奨)
npm run stg:deploy                  # ビルド → プッシュ → ECS ローリングデプロイ
npm run stg:deploy:only             # デプロイのみ (イメージ更新後)
npm run stg:status                  # ECS サービス状態確認
```

`stg:deploy:only` は内部で:

```bash
aws-vault exec cargo-tracker-stg -- aws ecs update-service \
  --cluster cargo-tracker-staging-cluster \
  --service cargo-tracker-staging-svc \
  --force-new-deployment
```

を実行します。

### 12. Application Manager (リソースグループ)

タグベースのリソースグループを作成し、全リソースを一元管理します。

#### 12.1 リソース定義

```hcl
resource "aws_resourcegroups_group" "app" {
  name        = "${local.resource_name}-app"
  description = "Cargo Tracker (Haskell) staging environment resource group"

  resource_query {
    query = jsonencode({
      ResourceTypeFilters = ["AWS::AllSupported"]
      TagFilters = [{
        Key    = "ResourceGroupName"
        Values = [local.tags_name]
      }]
    })
  }
}
```

#### 12.2 タグ設計

全モジュールで `ResourceGroupName` タグを統一的に付与します。

```hcl
tags = {
  Name              = "<リソース名>"
  ResourceGroupName = var.tags_name  # cargo-tracker-staging
  Project           = "cargo-tracker"
  Environment       = "staging"
  ManagedBy         = "terraform"
}
```

#### 12.3 プロビジョニング手順

作業ディレクトリ: `ops/terraform/live/stage/ssm/appmanager`

```bash
terraform init --backend-config=backend.hcl
terraform plan
terraform apply
```

### 13. EC2 キーペアの作成 (踏み台サーバー用・任意)

```bash
aws-vault exec cargo-tracker-stg -- aws ec2 create-key-pair \
  --key-name cargo-tracker-stg-bastion \
  --key-type rsa \
  --region ap-northeast-1 \
  --query "KeyMaterial" \
  --output text > cargo-tracker-stg-bastion.pem

chmod 400 cargo-tracker-stg-bastion.pem

# 公開鍵の生成
ssh-keygen -y -f cargo-tracker-stg-bastion.pem > cargo-tracker-stg-bastion.pem.pub
```

> **重要**: 秘密鍵ファイル (`.pem`) は再ダウンロードできません。安全な場所に保管してください。

### 14. 踏み台サーバーのセットアップ (任意)

作業ディレクトリ: `ops/terraform/live/mgmt/stage/bastion`

1. `secret.tfvars` ファイルを作成します

```text
vpc_id     = "<ステージング VPC の ID>"
subnet_ids = ["<パブリックサブネット 1>", "<パブリックサブネット 2>"]
postgres_config = {
  address = "<RDS エンドポイント>"
  port    = "5432"
}
```

2. Terraform を実行します

```bash
terraform init --backend-config=backend.hcl
terraform plan --var-file=secret.tfvars
terraform apply --var-file=secret.tfvars
```

3. RDS への接続

```bash
# SSH トンネル経由
ssh -L 5432:<RDS エンドポイント>:5432 ec2-user@<踏み台 IP> -i cargo-tracker-stg-bastion.pem

# 別ターミナルで DB に接続
psql -h 127.0.0.1 -p 5432 -U cargo_tracker -d cargo_tracker_staging
```

### 15. Route 53・カスタムドメインの設定

ALB の DNS 名に対して Route 53 の Alias レコードを作成します。
HTTPS は ACM 証明書 (`stg.cargo-tracker.example.com`) を ECS モジュールの `certificate_arn` 変数に設定します。

```hcl
module "ecs" {
  source          = "../../../../modules/services/ecs"
  certificate_arn = var.certificate_arn  # ACM 証明書の ARN
}

resource "aws_route53_record" "stg" {
  zone_id = var.route53_zone_id
  name    = "stg.cargo-tracker.example.com"
  type    = "A"
  alias {
    name                   = aws_lb.alb.dns_name
    zone_id                = aws_lb.alb.zone_id
    evaluate_target_health = true
  }
}
```

---

## デプロイ

### デプロイフロー

```plantuml
@startuml

title デプロイフロー

|開発 PC / GitHub Actions|
start
:Stack build + Docker build\n(マルチステージ);

:docker push\n(ECR にプッシュ、SHA タグ);

|AWS|
:dbmate up\n(マイグレーション適用);
note right: 初回起動時のみ、または\nSchema 変更時に実行

:ECS Rolling Update\n(force-new-deployment);
note right: aws ecs update-service\n--force-new-deployment

:ALB ヘルスチェック通過待ち\n(/health に 200 OK);

stop

@enduml
```

### デプロイタスク

```bash
# 典型的なデプロイフロー
npm run docker:build                # 1. Stack build + Docker イメージビルド
npm run stg:ecr:push                # 2. ECR にプッシュ
npm run stg:deploy:only             # 3. ECS をローリングデプロイ

# または一括実行
npm run stg:deploy
```

---

## アップグレード

### RDS メジャーバージョンアップグレード

Blue/Green Deployment を利用して、ダウンタイムを最小限に抑えたメジャーバージョンアップグレードを実行します。

#### 手順

1. 共有変数モジュールの `db_engine_version` を変更 (例: `16.4` → `17.1`)
2. `terraform apply` を実行 (Blue/Green Deployment が自動実行)
3. `terraform plan` で "No changes." を確認

> **注意**: Blue/Green Deployment は長時間かかるため、`aws-vault` のセッショントークンが途中で失効する場合があります。その場合は State ロック解除 → errored.tfstate のプッシュ → 手動 Switchover の順でリカバリしてください。

### GHC / ライブラリアップグレード

Haskell 側のアップグレードは Docker イメージの再ビルドのみで反映されます。

1. `stack.yaml` の resolver を新 LTS に変更
2. `stack build` でローカル検証
3. テスト + arch-check が全件 Pass を確認
4. `npm run stg:deploy` でステージング反映

---

## 環境廃棄

プロビジョニング済みのステージング環境を廃棄する場合は、**構築時と逆の順序** で実行します。

### 廃棄フロー

```plantuml
@startuml

title ステージング環境廃棄フロー

start
:Application Manager 削除\n(リソースグループ);
:ECS 削除\n(Fargate + ALB);
:ECR リポジトリ削除;
:AWS Backup 削除;
:RDS 削除;
:VPC 削除;
note right: 踏み台サーバーがある場合は\n先に削除してください
:SSM パラメータストア削除;
stop

@enduml
```

各リソースの削除コマンド:

```bash
# 各リソースの作業ディレクトリで実行
terraform init --backend-config=backend.hcl
terraform destroy
```

> **注意**: `secret.tfvars` を使用しているリソース (SSM、踏み台等) は `terraform destroy --var-file=secret.tfvars` を使用してください。

---

## データバックアップ

### AWS Backup (自動バックアップ)

| ルール | スケジュール | 保持期間 |
|--------|-------------|---------|
| daily | 毎日 JST 0:00 | 7 日 |
| weekly | 毎週日曜 JST 0:00 | 30 日 |

#### AWS Backup からのリストア

1. AWS コンソール → AWS Backup → バックアップボールト → リカバリポイントを選択
2. 「復元」をクリック → リストア先の DB インスタンス識別子を指定
3. リストア完了後、Secrets Manager / SSM の DB エンドポイントを更新 → ECS サービス再デプロイ

> **警告**: AWS Backup からのリストアは既存インスタンスへの上書きではなく、新しい RDS インスタンスとして作成されます。

### SSH トンネル経由のバックアップ (手動)

```bash
# 1. SSH トンネルを確立
ssh -L 5432:<RDS エンドポイント>:5432 ec2-user@<踏み台 IP> -i cargo-tracker-stg-bastion.pem

# 2. 全 DB ダンプ
PGPASSWORD=<パスワード> pg_dump -h 127.0.0.1 -p 5432 -U cargo_tracker -d cargo_tracker_staging -Fc -f cargo_tracker_staging_backup.dump

# 3. リストア
PGPASSWORD=<パスワード> pg_restore -h 127.0.0.1 -p 5432 -U cargo_tracker -d cargo_tracker_staging --clean --if-exists cargo_tracker_staging_backup.dump
```

---

## テスト

Terraform コードの品質を保証するため、3 段階のテスト戦略を採用しています。

| テスト | ツール | AWS 接続 | 対象 |
|-------|--------|:--------:|------|
| ローカルテスト | LocalStack (Docker) | 不要 | S3, SSM 等の基本サービス |
| 単体テスト | Terratest (Go) | 不要 | `terraform validate` による構文検証 |
| 結合テスト | Terratest (Go) | 必要 | S3 backend 付きの構文検証 |

### ローカルテスト (LocalStack)

```bash
# LocalStack 起動
docker compose -f ops/terraform/test/docker-compose.yml up -d
cd ops/terraform/test
go test -v -timeout 30m ./local/...
```

### 単体テスト (Terratest)

```bash
cd ops/terraform/test
go test -v -timeout 30m ./unit/...
```

### 結合テスト (Terratest)

```bash
cd ops/terraform/test
aws-vault exec cargo-tracker-stg -- go test -v -timeout 30m ./integration/...
```

---

## トラブルシューティング

### aws-vault 関連

| 症状 | 原因 | 対処 |
|------|------|------|
| `an AWS region is required` | `~/.aws/config` に region 未設定 | プロファイルに `region=ap-northeast-1` を追加 |
| `InvalidClientTokenId` (IAM 操作) | STS 一時認証情報の制約 | `aws-vault exec --no-session` を使用 |
| `EntityAlreadyExists` (OIDC) | OIDC プロバイダーは AWS アカウントに 1 つ | `data` ソースで既存を参照 |

### Terraform 関連

| 症状 | 原因 | 対処 |
|------|------|------|
| `terraform init` が失敗 | S3 バケット未作成 | `live/global/s3` を先に適用 |
| 状態ロックエラー | 前回の apply が中断 | `terraform force-unlock <lock-id>` |
| `backend-config` エラー | `backend.hcl` が見つからない | 作業ディレクトリを確認 |

### ECS 関連

| 症状 | 原因 | 対処 |
|------|------|------|
| ALB/TG 名が長すぎるエラー | リソース名が 32 文字超過 | 短い `resource_name` を使用 (`cargo-tg` 等) |
| ターゲットが unhealthy | ヘルスチェックパスが不正 | `/health` に設定 (Haskell アプリの自作エンドポイント) |
| コンテナが 503 を返す | DB 接続失敗 | RDS セキュリティグループ・`DATABASE_URL` を確認 |
| 環境変数変更が反映されない | `ignore_changes` 設定 | AWS CLI でタスク定義の新リビジョンを登録し `--force-new-deployment` |
| GHC バイナリ起動失敗 | 不足ライブラリ | Docker イメージに `libpq5 libgmp10` が含まれるか確認 |
| `start_period` 超過で unhealthy | 初回マイグレーションで起動遅延 | `start_period` を 60s → 120s に延長 |

### RDS 関連

| 症状 | 原因 | 対処 |
|------|------|------|
| 接続できない | セキュリティグループ設定 | プライベートサブネット (ECS) からの 5432 アクセスを許可 |
| 認証エラー | SSM パラメータの値が不正 | パラメータストアの値を確認 |
| `terraform destroy` が遅い | 削除保護が有効 | `deletion_protection = false` にしてから再実行 |
| Blue/Green 後に差異 | コンソールで手動実行 | `terraform apply -refresh-only` で State を同期 |
| Blue/Green 作成失敗 | PK なしテーブルが存在 | 全テーブルに Primary Key を追加 (data-model.md 参照、すべて PK あり) |

### ネットワーク関連

| 症状 | 原因 | 対処 |
|------|------|------|
| プライベートサブネットから外部接続不可 | NAT Gateway 未設定 | VPC 設定を確認 |
| 踏み台サーバーに SSH 接続できない | セキュリティグループ | 自分の IP からのインバウンドを許可 |
| ALB から ECS に到達不可 | SG 設定 | ECS SG に ALB SG からの 8080 インバウンド許可 |

---

## セキュリティチェックリスト

- [ ] `secret.tfvars` が `.gitignore` に追加されている
- [ ] DB 認証情報 + JWT 鍵が SSM パラメータストアで管理されている
- [ ] RDS がプライベートサブネットに配置されている
- [ ] 踏み台サーバーの SSH アクセスが IP 制限されている
- [ ] OIDC 認証で GitHub Actions と AWS が連携している
- [ ] S3 バケットの暗号化が有効になっている
- [ ] DynamoDB の状態ロックが設定されている
- [ ] ALB で HTTPS (TLS 1.3) が有効になっている
- [ ] ACM 証明書が設定されている (stg.cargo-tracker.example.com)
- [ ] CloudWatch Logs に katip JSON 構造化ログが出力されている

---

## 関連ドキュメント

- [アプリケーション開発環境セットアップ手順書](アプリケーション開発環境セットアップ手順書.md)
- [開発環境セットアップ手順書](開発環境セットアップ手順書.md)
- [AWS プロダクション環境セットアップ手順書](AWSプロダクション環境セットアップ手順書.md)
- [インフラアーキテクチャ](../design/architecture_infrastructure.md)
- [非機能要件](../design/non_functional.md)
- [運用要件](../design/operation.md)
