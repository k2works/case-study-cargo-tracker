---
title: 運用要件定義 - 国際貨物輸送管理システム（Flix 版）
description: 国際貨物輸送管理システムの運用フロー、監視設計、バックアップ・リカバリ、障害対応、変更管理、セキュリティ運用、キャパシティ管理を定義する。
published: true
date: 2026-07-31T00:00:00.000Z
tags: design, operation, monitoring, backup, incident, sre
---

# 運用要件定義 - 国際貨物輸送管理システム（Flix 版）

## 1. 概要

### 1.1 目的

本ドキュメントは、国際貨物輸送管理システムの本番稼働後における運用手順・方針を定義する。運用設計の目標は以下のとおりである。

- **自動化優先**: 繰り返し発生する運用タスクはすべてスクリプト化・自動化し、人的ミスを排除する
- **IaC 管理**: インフラリソースはすべて Terraform で管理し、手動操作による構成ドリフトを防止する
- **観測可能性（Observability）の確保**: CloudWatch によるメトリクス・ログ・アラートを統合し、問題を早期に検知する
- **SLA/SLO 遵守**: 合意した SLA（99.9%）および内部 SLO（99.95%）を常に満たす状態を維持する

### 1.2 SLA / SLO

| 区分 | SLA（外部合意） | SLO（内部目標） | 月間許容停止時間 |
|---|---|---|---|
| システム全体稼働率 | 99.9% | 99.95% | SLA: 43.8 分 / SLO: 21.9 分 |
| 貨物追跡機能稼働率 | 99.99% | 99.995% | SLA: 4.4 分 / SLO: 2.2 分 |

### 1.3 RTO / RPO

| 障害種別 | RTO | RPO |
|---|---|---|
| ECS タスク異常終了 | 5 分（Auto Recovery） | 0（ステートレス） |
| RDS フェイルオーバー（Multi-AZ） | 60〜120 秒 | 数秒（同期レプリケーション） |
| AZ 全体障害 | 30 分 | 1 時間（Point-in-Time Recovery 基準） |
| リージョン全体障害 | 4 時間（手動フェイルオーバー） | 24 時間（日次スナップショット基準） |

### 1.4 運用基盤

| コンポーネント | 構成 |
|---|---|
| ECS クラスター | `cargo-tracker-cluster`（Fargate、CPU 512 / メモリ 1024 MB、min 2 / max 10） |
| RDS | PostgreSQL 16 Multi-AZ（`db.t3.medium`、自動バックアップ 7 日） |
| ALB | HTTPS:443 → ECS:8080 |
| 監視 | CloudWatch Logs・Metrics・Alarms・Dashboard |
| ネットワーク | VPC 10.0.0.0/16（ap-northeast-1a / ap-northeast-1c） |
| アプリケーション | Flix 実装（JVM 25 上の実行可能 JAR）。ヘルスチェックは自作 `/health/live`・`/health/ready`、メトリクスは `/metrics` |

> **Flix 実装に固有の運用上の注意**
>
> - **ヘルスチェック**: Actuator 相当が存在しないため自作エンドポイントを使用する。`/health/live` は DB を見ず、`/health/ready` のみ DB 疎通を検査する（[非機能要件](non_functional.md) 3.4 参照）
> - **メトリクス**: Micrometer 相当が存在しないため、アプリ内カウンタを `/metrics` で公開し、CloudWatch へは埋め込みメトリクス形式のログとして送出する
> - **セッション**: 既定はインメモリのため、タスク入れ替え時にログインセッションが失われる。ローリングデプロイでは ALB のスティッキーセッションを有効にし、無停止性が要求される場合は `Session` 効果のハンドラを外部ストア実装へ差し替える
> - **デプロイ所要時間**: Flix のコンパイルは Java より長い。CI のビルドキャッシュが失われた場合、リリース所要時間が想定を超えることがある

---

## 2. 運用フロー

### 2.1 定常運用カレンダー

```plantuml
@startgantt
<style>
ganttDiagram {
  task {
    BackGroundColor lightblue
    LineColor blue
  }
}
</style>
title 定常運用カレンダー

Project starts 2026-04-01

[日次: ヘルスチェック確認] lasts 1 day
[日次: CloudWatch ダッシュボード確認] lasts 1 day
[日次: バックアップ完了確認] lasts 1 day
[日次: エラーログ確認] lasts 1 day

[週次: セキュリティグループ棚卸] lasts 5 days
[週次: コスト最適化レポート確認] lasts 5 days
[週次: ECS タスク定義レビュー] lasts 5 days

2026-04-01 to 2026-04-30 are named [月次運用期間]
[月次: セキュリティパッチ適用] lasts 30 days
[月次: 容量・スケーリングレビュー] lasts 30 days
[月次: SLA/SLO レポート生成] lasts 30 days
[月次: バックアップリストアテスト] lasts 30 days
[月次: アクセス権限棚卸] lasts 30 days

2026-04-01 to 2026-12-31 are named [年次運用期間]
[年次: DR 訓練（リージョン障害シミュレーション）] lasts 365 days
[年次: ライセンス更新確認] lasts 365 days
[年次: 監査ログレビュー] lasts 365 days
[年次: 非機能要件の見直し] lasts 365 days
@endgantt
```

### 2.2 日次運用

毎日 09:00 JST に以下の確認を実施する（所要時間: 15 分以内）。

**確認項目**:

- [ ] CloudWatch ダッシュボード（`cargo-tracker-dashboard`）で全メトリクスが正常範囲内であることを確認
- [ ] ECS サービスの HealthyHostCount が min 値（2）以上であることを確認
- [ ] 前夜の RDS 自動バックアップが正常完了していることを確認
- [ ] Alarm 状態のアラートがないことを確認（存在する場合は障害対応フローへ）

```bash
# ECS サービス状態確認
aws ecs describe-services \
  --cluster cargo-tracker-cluster \
  --services cargo-tracker-service \
  --query 'services[0].{Status:status,Running:runningCount,Desired:desiredCount}'

# CloudWatch アラーム確認
aws cloudwatch describe-alarms \
  --alarm-name-prefix "cargo-tracker" \
  --state-value ALARM

# RDS バックアップ確認（直近 1 件）
aws rds describe-db-snapshots \
  --db-instance-identifier cargo-tracker-db \
  --snapshot-type automated \
  --query 'sort_by(DBSnapshots, &SnapshotCreateTime)[-1].{Time:SnapshotCreateTime,Status:Status}'
```

### 2.2.1 業務運用の暫定手順（通知機能が未実装のため）

**v1.0.0 では、システムからの通知（メール・チャット）を実装しない。**
US12（確定経路の通知）は将来リリースへ送っている
（[ADR-0007](../adr/ADR-0007-defer-external-acl-and-scope-v1.md)・リリース計画）。

通知が無いまま引き渡しを行うと、**「引き渡したのに誰も着手していない」**という
状態が誰にも気付かれずに続く。予約は `ROUTING_ASSIGNED` になっているため、
営業担当者からは「渡した」ように見える。

そこで、通知の代わりに**人が定期的に見る手順**を運用として定める。

| 頻度 | 担当 | 手順 |
| :--- | :--- | :--- |
| 毎営業日 09:30 | 経路設計者 | 貨物予約一覧を `ROUTING_ASSIGNED` で絞り込み、**前営業日以前の引き渡しが残っていないか**を確認する |
| 毎営業日 17:00 | 営業担当者 | 自分が引き渡した予約の状態を確認し、当日中に着手されていないものを経路設計者へ口頭・チャットで伝える |

**この手順は暫定である。** 人が見に行くことに依存する運用は、忙しい日に飛ぶ。
通知機能（US12）を実装した時点で本節を削除する。

### 2.3 週次運用

毎週月曜日 10:00 JST に実施する（所要時間: 30 分以内）。

**確認・作業項目**:

- [ ] セキュリティグループのインバウンドルールに不審な変更がないか確認
- [ ] AWS Cost Explorer でコストが予算範囲内（前週比 ±20% 以内）であることを確認
- [ ] ECR の未使用イメージを確認し、30 日以上前のイメージをクリーンアップ
- [ ] ECS タスク定義の最新バージョンに不審なリビジョンがないか確認

```bash
# 未使用 ECR イメージ一覧（30 日以上前）
aws ecr describe-images \
  --repository-name cargo-tracker-app \
  --query 'imageDetails[?imagePushedAt<=`'$(date -d "30 days ago" +%Y-%m-%dT%H:%M:%S)'`].[imageDigest,imagePushedAt]' \
  --output table
```

### 2.4 月次運用

毎月第 1 営業日の午前中に実施する（所要時間: 2 時間以内）。

**作業項目**:

- [ ] OS・ミドルウェアのセキュリティパッチ確認と適用計画策定（→ 変更管理フローへ）
- [ ] RDS Point-in-Time Recovery のリストアテスト（→ バックアップ設計参照）
- [ ] SLA/SLO 達成状況レポートを作成し関係者へ共有
- [ ] ECS Auto Scaling の実績確認とスケーリング閾値の調整検討
- [ ] IAM ユーザー・ロールのアクセス権限棚卸

```bash
# 月次 SLA レポート用: 過去 30 日の 5xx エラー率取得
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name HTTPCode_Target_5XX_Count \
  --dimensions Name=LoadBalancer,Value=<ALB-ARN> \
  --start-time $(date -d "30 days ago" --iso-8601=seconds) \
  --end-time $(date --iso-8601=seconds) \
  --period 2592000 \
  --statistics Sum
```

### 2.5 年次運用

毎年 1 月（年度初め）に実施する。

**作業項目**:

- [ ] DR 訓練: リージョン全体障害を想定したフェイルオーバー手順の実動演習（4 時間枠確保）
- [ ] AWS ライセンス・サポートプランの更新確認
- [ ] 監査ログ（`/ecs/cargo-tracker/audit`）の 1 年分をアーカイブして S3 に保存
- [ ] 非機能要件（SLA/SLO/RTO/RPO）の見直しと関係者合意

---

## 3. 監視設計

### 3.1 監視・アラートフロー

```plantuml
@startuml
title 監視・アラートフロー

actor オペレーター
participant "CloudWatch\nAlarms" as CWA
participant "SNS Topic\ncargo-tracker-alerts" as SNS
participant "Slack\n#cargo-ops" as Slack
participant "PagerDuty" as PD
participant "オンコール担当" as Oncall

CWA -> SNS : アラーム状態遷移\n(OK → ALARM)
SNS -> Slack : Warning / Critical\n通知送信
SNS -> PD : Critical のみ\nインシデント作成

alt Warning アラート
  Slack -> オペレーター : 通知確認\n（15 分以内に対応）
  オペレーター -> CWA : 状況確認\n(describe-alarms)
  オペレーター -> オペレーター : 根本原因調査
  alt 自己解決
    オペレーター -> Slack : 解決コメント記録
  else 解決不可
    オペレーター -> PD : Critical にエスカレーション
  end
else Critical アラート
  PD -> Oncall : ページング（5 分以内に応答）
  Oncall -> Oncall : 障害対応フロー開始
  Oncall -> Slack : 対応状況を #cargo-ops に逐次報告
  alt 30 分以内に解決しない
    Oncall -> Oncall : L2 エスカレーション\n（インフラリード・開発リード）
  end
  alt SLA 違反リスクあり
    Oncall -> オペレーター : 顧客通知・経営層報告
  end
end

CWA -> SNS : アラーム解消\n(ALARM → OK)
SNS -> Slack : 解消通知
SNS -> PD : インシデントクローズ

@enduml
```

### 3.2 監視項目とアラート閾値

#### 3.2.1 アプリケーションメトリクス

| メトリクス | ソース | Warning | Critical | 対応 |
|---|---|---|---|---|
| HTTP 5xx エラー率 | ALB | 1% | 5% | 障害対応フロー |
| HTTP レスポンスタイム p95 | ALB | 500ms | 1,000ms | アプリ調査 |
| ECS HealthyHostCount | ECS | 1 | 0 | 即時 ECS 復旧 |
| ECS CPU 使用率 | ECS | 70% | 90% | スケールアウト確認 |
| ECS メモリ使用率 | ECS | 80% | 95% | スケールアウト確認 |

#### 3.2.2 インフラメトリクス

| メトリクス | ソース | Warning | Critical | 対応 |
|---|---|---|---|---|
| RDS CPU 使用率 | RDS | 70% | 90% | クエリ最適化 |
| RDS 接続数 | RDS | 上限の 70% | 上限の 90% | 接続プール調整 |
| RDS 空きストレージ | RDS | 20% 以下 | 10% 以下 | ストレージ拡張 |
| RDS レプリカラグ | RDS | 30 秒 | 120 秒 | フェイルオーバー検討 |

#### 3.2.3 性能 SLO メトリクス

| 操作 | p95 目標 | 計測ポイント |
|---|---|---|
| 貨物追跡検索 | 200ms | ALB アクセスログ + APM |
| 荷役作業登録 | 500ms | ALB アクセスログ + APM |
| 請求書生成 | 1,500ms | ALB アクセスログ + APM |

### 3.3 CloudWatch アラーム設定

```bash
# HTTP 5xx エラー率 Critical アラーム作成
aws cloudwatch put-metric-alarm \
  --alarm-name "cargo-tracker-5xx-critical" \
  --alarm-description "HTTP 5xx エラー率が 5% を超過" \
  --namespace "AWS/ApplicationELB" \
  --metric-name "HTTPCode_Target_5XX_Count" \
  --dimensions Name=LoadBalancer,Value=<ALB-ARN> \
  --statistic Sum \
  --period 60 \
  --evaluation-periods 3 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --alarm-actions <SNS-ARN> \
  --treat-missing-data notBreaching

# ECS HealthyHostCount Critical アラーム作成
aws cloudwatch put-metric-alarm \
  --alarm-name "cargo-tracker-healthy-host-critical" \
  --alarm-description "ECS HealthyHostCount が 0 になった" \
  --namespace "AWS/ApplicationELB" \
  --metric-name "HealthyHostCount" \
  --dimensions Name=TargetGroup,Value=<TG-ARN> \
  --statistic Minimum \
  --period 60 \
  --evaluation-periods 1 \
  --threshold 1 \
  --comparison-operator LessThanThreshold \
  --alarm-actions <SNS-ARN>
```

### 3.4 ログ設計

| ログ種別 | CloudWatch ロググループ | 保持期間 | 用途 |
|---|---|---|---|
| アプリケーション | `/ecs/cargo-tracker` | 30 日 | デバッグ・障害調査 |
| 監査 | `/ecs/cargo-tracker/audit` | 1 年 | コンプライアンス対応 |
| ALB アクセスログ | S3 バケット（`cargo-tracker-alb-logs`） | 90 日 | 性能分析・セキュリティ |
| RDS 低速クエリ | RDS → CloudWatch | 14 日 | クエリ最適化 |

#### ログ検索例

```bash
# 直近 1 時間の ERROR ログを抽出
aws logs filter-log-events \
  --log-group-name "/ecs/cargo-tracker" \
  --start-time $(($(date +%s%3N) - 3600000)) \
  --filter-pattern "ERROR" \
  --query 'events[*].{Time:timestamp,Message:message}' \
  --output table

# 特定トレース ID のログ追跡
aws logs filter-log-events \
  --log-group-name "/ecs/cargo-tracker" \
  --filter-pattern '{ $.traceId = "abc-123" }' \
  --start-time $(($(date +%s%3N) - 86400000))

# 低速クエリ（1 秒以上）の確認
aws logs filter-log-events \
  --log-group-name "/aws/rds/instance/cargo-tracker-db/slowquery" \
  --filter-pattern "Query_time: [qt>=1, ...]" \
  --start-time $(($(date +%s%3N) - 86400000))
```

### 3.5 CloudWatch ダッシュボード

ダッシュボード名: `cargo-tracker-dashboard`

**ウィジェット構成**:

- **SLA ステータス**: 直近 30 日の稼働率（5 分粒度）
- **HTTP メトリクス**: 2xx / 4xx / 5xx の時系列グラフ
- **ECS リソース**: CPU / メモリ使用率（タスクごと）
- **RDS ステータス**: CPU / 接続数 / レプリカラグ
- **レスポンスタイム**: p50 / p95 / p99 の時系列グラフ

---

## 4. バックアップ・リカバリ設計

### 4.1 バックアップ方式

| 対象 | 方式 | スケジュール | 保持期間 | 担当 |
|---|---|---|---|---|
| RDS（自動） | スナップショット（フル） | 毎日 02:00 JST | 7 日間 | AWS 自動 |
| RDS（手動） | スナップショット（フル） | 月次リリース前 | 無期限（手動管理） | 運用担当 |
| ECS タスク定義 | IaC（Terraform State） | Git push 時 | Git 履歴で管理 | CI/CD |
| アプリケーションログ | CloudWatch → S3 Export | 月次 | 1 年 | Lambda 自動 |
| 監査ログ | CloudWatch → S3 Export | 月次 | 7 年 | Lambda 自動 |

### 4.2 RDS バックアップ手順

#### 手動スナップショット作成

```bash
# リリース前など任意タイミングで手動スナップショット作成
aws rds create-db-snapshot \
  --db-instance-identifier cargo-tracker-db \
  --db-snapshot-identifier manual-snapshot-$(date +%Y%m%d-%H%M%S)

# スナップショット作成完了待ち（最大 30 分）
aws rds wait db-snapshot-available \
  --db-snapshot-identifier manual-snapshot-$(date +%Y%m%d)

# スナップショット一覧確認
aws rds describe-db-snapshots \
  --db-instance-identifier cargo-tracker-db \
  --query 'DBSnapshots[*].{ID:DBSnapshotIdentifier,Time:SnapshotCreateTime,Status:Status}' \
  --output table
```

### 4.3 リストア手順

#### 4.3.1 Point-in-Time Recovery（AZ 障害時）

RTO 目標: 30 分以内

```bash
# Step 1: リストア先の DB インスタンスを新規作成（既存は残す）
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier cargo-tracker-db \
  --target-db-instance-identifier cargo-tracker-db-restore \
  --restore-time 2026-04-01T01:00:00Z  # 復旧したい時点の UTC 時刻

# Step 2: 復旧 DB が利用可能になるまで待機（最大 30 分）
aws rds wait db-instance-available \
  --db-instance-identifier cargo-tracker-db-restore

# Step 3: エンドポイント確認
aws rds describe-db-instances \
  --db-instance-identifier cargo-tracker-db-restore \
  --query 'DBInstances[0].Endpoint.Address'

# Step 4: Secrets Manager の接続情報を新エンドポイントに更新
aws secretsmanager update-secret \
  --secret-id cargo-tracker/db-credentials \
  --secret-string '{"host":"<new-endpoint>","port":5432,"dbname":"cargotracker","username":"<user>","password":"<pass>"}'

# Step 5: ECS サービスを再デプロイして新 DB に接続切り替え
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --force-new-deployment
```

#### 4.3.2 スナップショットからのリストア（完全復旧）

```bash
# Step 1: 利用可能なスナップショット一覧を確認
aws rds describe-db-snapshots \
  --db-instance-identifier cargo-tracker-db \
  --query 'sort_by(DBSnapshots, &SnapshotCreateTime)[-5:].{ID:DBSnapshotIdentifier,Time:SnapshotCreateTime}' \
  --output table

# Step 2: スナップショットから新 DB インスタンスを復元
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier cargo-tracker-db-restored \
  --db-snapshot-identifier <snapshot-id> \
  --db-instance-class db.t3.medium \
  --multi-az \
  --vpc-security-group-ids <sg-id>

# Step 3 以降: Point-in-Time Recovery の Step 2〜5 と同様
```

### 4.4 月次リストアテスト

毎月第 1 営業日にリストアテストを実施し、RTO が目標内（30 分）で達成できることを確認する。

**テスト手順**:

1. 自動スナップショットの最新版を確認する
2. ステージング環境でスナップショットからリストアを実行する
3. リストア完了後、基本的な動作確認（貨物追跡・ログイン）を行う
4. 所要時間を記録し、RTO 目標（30 分）内であることを確認する
5. テスト結果を月次運用レポートに記録する

---

## 5. 障害対応設計

### 5.1 障害対応フロー

```plantuml
@startuml
title 障害対応フロー

start

:CloudWatch アラーム検知;
:Slack #cargo-ops / PagerDuty 通知;

:オンコール担当が 5 分以内に応答;

:障害スコープを確認;
note right
  - ALB ヘルスチェック確認
  - ECS サービス状態確認
  - RDS 状態確認
  - CloudWatch メトリクス確認
end note

if (ECS タスク障害?) then (Yes)
  :ECS タスク障害対応フロー;
  :異常タスクの識別（タスク ID 取得）;
  :ログ確認（/ecs/cargo-tracker）;
  if (自動復旧（5 分以内）?) then (Yes)
    :状況を Slack に記録;
    stop
  else (No)
    :手動で ECS サービスを強制再デプロイ;
    :aws ecs update-service --force-new-deployment;
    if (復旧成功?) then (Yes)
      :根本原因分析（5 営業日以内）;
      stop
    else (No)
      :直前バージョンへロールバック;
    end if
  end if
else (No)
  if (RDS フェイルオーバー?) then (Yes)
    :RDS フェイルオーバー対応フロー;
    :Multi-AZ 自動フェイルオーバー開始確認;
    :60〜120 秒待機;
    if (自動フェイルオーバー成功?) then (Yes)
      :ECS タスクの DB 接続再確立確認;
      :状況を Slack に記録;
      stop
    else (No)
      :手動フェイルオーバー実行;
      :aws rds reboot-db-instance --force-failover;
    end if
  else (No)
    if (AZ / リージョン障害?) then (Yes)
      :障害スコープを AWS Health Dashboard で確認;
      if (AZ 障害) then (Yes)
        :ECS 残存 AZ でのタスク継続確認（30 分以内）;
        :RDS Point-in-Time Recovery 実施;
      else (リージョン障害)
        :インシデント管理者（L2）を招集;
        :手動フェイルオーバー計画を立案（4 時間以内）;
        :ステークホルダーへ状況報告;
      end if
    else (No)
      :アプリケーション障害として調査;
      :ログ・メトリクスから原因特定;
    end if
  end if
end if

:ロールバック実施;
:復旧確認（ヘルスチェック OK）;
:インシデントレポート作成（24 時間以内）;
stop

@enduml
```

### 5.2 ECS タスク障害対応

#### 検知条件

- ECS HealthyHostCount が 1 以下（Warning）または 0（Critical）
- ALB ヘルスチェック失敗率が 50% を超過

#### 対応手順

```bash
# Step 1: 障害タスクの特定
aws ecs list-tasks \
  --cluster cargo-tracker-cluster \
  --service-name cargo-tracker-service \
  --desired-status STOPPED

# Step 2: タスク停止理由の確認
aws ecs describe-tasks \
  --cluster cargo-tracker-cluster \
  --tasks <TASK-ARN> \
  --query 'tasks[0].{StopCode:stopCode,Reason:stoppedReason,ContainerReason:containers[0].reason}'

# Step 3: アプリケーションログ確認（直近 100 行）
aws logs get-log-events \
  --log-group-name "/ecs/cargo-tracker" \
  --log-stream-name "ecs/cargo-tracker-app/<TASK-ID>" \
  --limit 100

# Step 4: 自動復旧しない場合、強制再デプロイ
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --force-new-deployment

# Step 5: 新タスクが正常起動するまで待機（最大 5 分）
aws ecs wait services-stable \
  --cluster cargo-tracker-cluster \
  --services cargo-tracker-service
```

### 5.3 RDS フェイルオーバー対応

#### 検知条件

- RDS のステータスが `available` 以外
- RDS レプリカラグが 120 秒を超過

#### 対応手順

```bash
# Step 1: RDS 現在の状態確認
aws rds describe-db-instances \
  --db-instance-identifier cargo-tracker-db \
  --query 'DBInstances[0].{Status:DBInstanceStatus,AZ:AvailabilityZone,MultiAZ:MultiAZ}'

# Step 2: 自動フェイルオーバーが発生していない場合、手動で実行
aws rds reboot-db-instance \
  --db-instance-identifier cargo-tracker-db \
  --force-failover

# Step 3: フェイルオーバー完了確認（120 秒程度）
aws rds wait db-instance-available \
  --db-instance-identifier cargo-tracker-db

# Step 4: 新エンドポイントに接続できることを確認
ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier cargo-tracker-db \
  --query 'DBInstances[0].Endpoint.Address' --output text)
echo "新エンドポイント: ${ENDPOINT}"

# Step 5: ECS タスクが新エンドポイントに接続できているか確認
aws ecs describe-services \
  --cluster cargo-tracker-cluster \
  --services cargo-tracker-service \
  --query 'services[0].{Running:runningCount,Desired:desiredCount}'
```

### 5.4 連絡体制とエスカレーション

| レベル | 担当 | 対応時間 | 連絡方法 |
|---|---|---|---|
| L1: 初期対応 | オンコール担当（輪番） | 5 分以内に応答 | PagerDuty + Slack |
| L2: エスカレーション | インフラリード / 開発リード | 30 分以内 | 電話 + Slack |
| L3: 経営報告 | システムマネージャー | SLA 違反リスク時 | メール + 電話 |
| 顧客通知 | 営業担当 | SLA 違反確定時 | メール・電話 |

**エスカレーション基準**:

- L1 → L2: 初期対応開始から 30 分以内に解決の見込みがない場合
- L2 → L3: SLA 違反（月間停止時間 43.8 分の 80% = 35 分を超える場合）
- 顧客通知: SLA 違反が確定した場合（確定後 2 時間以内に通知）

### 5.5 インシデントレポート

インシデント発生後 24 時間以内に以下の形式でレポートを作成し、関係者へ共有する。

**インシデントレポートテンプレート**:

```
## インシデントレポート

- **インシデント ID**: INC-YYYYMMDD-NNN
- **発生日時**: YYYY-MM-DD HH:MM JST
- **検知日時**: YYYY-MM-DD HH:MM JST
- **解消日時**: YYYY-MM-DD HH:MM JST
- **停止時間**: X 分 Y 秒
- **影響範囲**: [貨物追跡 / 全機能 / 特定機能]
- **SLA への影響**: [なし / あり（月次累積: XX 分）]

### 障害の経緯

1. HH:MM - [事象]
2. HH:MM - [対応内容]
3. HH:MM - [復旧確認]

### 根本原因

[根本原因の詳細]

### 再発防止策

| 対策 | 担当 | 期限 |
|---|---|---|
| [対策内容] | [担当者] | [YYYY-MM-DD] |
```

---

## 6. 変更管理設計

### 6.1 リリースフロー

```plantuml
@startuml
title リリースフロー（GitHub Actions → ECS）

start

:開発者が feature ブランチで実装;
:Pull Request を main ブランチへ作成;
:コードレビュー + CI チェック\n（Lint / テスト / セキュリティスキャン）;

if (CI 全パス + レビュー承認?) then (Yes)
  :main ブランチへマージ;
  :GitHub Actions が自動起動;
  :Docker イメージビルド・ECR プッシュ;
  :ECS ステージング自動デプロイ\n（Rolling Update）;
  :ステージング環境での動作確認\n（自動 E2E テスト + 手動確認）;

  if (ステージング確認 OK?) then (Yes)
    :release タグを作成\n（例: v1.2.0）;
    :GitHub Actions が本番デプロイ待機;
    :本番デプロイ承認者がレビュー;

    if (本番デプロイ承認?) then (Yes)
      :本番 ECS Rolling Update 開始;
      note right
        最小タスク数を維持しながら
        新バージョンに順次切り替え
      end note
      :HealthCheck 通過後にロールアウト完了;
      :デプロイ後動作確認\n（5 分間のメトリクス確認）;

      if (デプロイ後 5 分間正常?) then (Yes)
        :リリース完了通知（Slack #cargo-ops）;
        stop
      else (No)
        :即時ロールバック実施;
        :インシデントフロー開始;
      end if
    else (拒否)
      :リリース中止;
      :原因を記録して次イテレーションへ;
      stop
    end if
  else (No)
    :ステージング環境で問題修正;
    :修正コミットを main にマージ → 再デプロイ;
  end if
else (No)
  :CI 失敗・レビュー指摘を修正;
  :Pull Request を更新;
end if

@enduml
```

### 6.2 デプロイコマンド

#### ステージングデプロイ（自動 / main push）

```bash
# GitHub Actions から自動実行される（手動実行する場合）
aws ecs update-service \
  --cluster cargo-tracker-cluster-staging \
  --service cargo-tracker-service \
  --task-definition cargo-tracker-app:latest \
  --force-new-deployment

# デプロイ完了待機
aws ecs wait services-stable \
  --cluster cargo-tracker-cluster-staging \
  --services cargo-tracker-service
```

#### 本番デプロイ（手動承認後）

```bash
# Step 1: ECR の最新イメージ SHA 確認
IMAGE_SHA=$(aws ecr describe-images \
  --repository-name cargo-tracker-app \
  --query 'sort_by(imageDetails, &imagePushedAt)[-1].imageDigest' \
  --output text)
echo "デプロイするイメージ: ${IMAGE_SHA}"

# Step 2: 本番デプロイ前の手動スナップショット取得
aws rds create-db-snapshot \
  --db-instance-identifier cargo-tracker-db \
  --db-snapshot-identifier pre-deploy-$(date +%Y%m%d-%H%M%S)

# Step 3: 本番 ECS Rolling Update
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --task-definition cargo-tracker-app:<NEW-REVISION> \
  --force-new-deployment

# Step 4: デプロイ完了を確認（最大 10 分）
aws ecs wait services-stable \
  --cluster cargo-tracker-cluster \
  --services cargo-tracker-service

# Step 5: デプロイ後 5 分間のエラー率確認
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name HTTPCode_Target_5XX_Count \
  --dimensions Name=LoadBalancer,Value=<ALB-ARN> \
  --start-time $(date -d "5 minutes ago" --iso-8601=seconds) \
  --end-time $(date --iso-8601=seconds) \
  --period 300 \
  --statistics Sum
```

### 6.3 ロールバック手順

#### アプリケーションのロールバック（ECS）

```bash
# Step 1: 前バージョンのタスク定義リビジョンを確認
aws ecs list-task-definitions \
  --family-prefix cargo-tracker-app \
  --status ACTIVE \
  --sort DESC \
  --query 'taskDefinitionArns[:5]' \
  --output table

# Step 2: 前バージョンの ECR イメージ SHA を確認
aws ecr describe-images \
  --repository-name cargo-tracker-app \
  --query 'sort_by(imageDetails, &imagePushedAt)[-2:].{Digest:imageDigest,PushedAt:imagePushedAt}' \
  --output table

# Step 3: 前バージョンのタスク定義でサービスを更新
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --task-definition cargo-tracker-app:<前バージョン番号> \
  --force-new-deployment

# Step 4: ロールバック完了確認
aws ecs wait services-stable \
  --cluster cargo-tracker-cluster \
  --services cargo-tracker-service

# Step 5: ロールバック後の動作確認
curl -f https://<ALB-DOMAIN>/health/ready
```

#### DB マイグレーションのロールバック（Forward マイグレーション方式）

> **注記**: Flyway の `undo` コマンド（`U{n}__{説明}.sql`）は **Flyway Teams / Enterprise ライセンス**が必要であり、Community 版では動作しない。本プロジェクトでは Community 版を前提とし、以下の **Forward マイグレーション方式** を採用する。

**Forward マイグレーション方式**:

スキーマ変更のロールバックは「新しいマイグレーションファイルで元の状態に戻す」ことで実現する。

```sql
-- 例: カラム追加のロールバックは新しいファイルでカラムを削除する
-- V3__add_cargo_status_column.sql  （本番反映済み）
ALTER TABLE cargos ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'PRELIMINARY';

-- V4__remove_cargo_status_column.sql  （ロールバック相当の forward マイグレーション）
ALTER TABLE cargos DROP COLUMN status;
```

**スキーマ変更を含むリリースの推奨パターン（Expand-Contract）**:

| フェーズ | 内容 | マイグレーション |
|---|---|---|
| **Expand** | 新しいカラム・テーブルを追加（旧コードと共存可能な形で） | `V{n}__expand_*.sql` |
| **Migrate** | データ移行・新コードへの切り替え | アプリケーションコードの更新 |
| **Contract** | 旧カラム・テーブルを削除（新コード完全切り替え後） | `V{n}__contract_*.sql` |

このパターンにより、ECS タスクのロールバック時でも旧コードが新スキーマと共存できる。

```bash
# スキーマ変更を含むリリースのロールバック手順

# Step 1: ECS タスクを旧バージョンイメージに戻す（アプリのみ）
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --task-definition cargo-tracker:<旧リビジョン番号>

# Step 2: DB スキーマは旧コードと共存可能であることを確認
# （Expand フェーズのマイグレーションが適用済みなら旧コードも動作する）

# Step 3: Contract フェーズのマイグレーションはロールバック完了を確認後に実施
# ※ Contract 前であれば旧コードで動作継続が可能

# Step 4: 動作確認
curl -f https://<ALB-DOMAIN>/health/ready
```

### 6.4 変更承認フロー

| 変更種別 | 影響範囲 | 承認者 | 申請方法 |
|---|---|---|---|
| ホットフィックス（緊急） | 限定的 | 開発リード 1 名 | Slack 承認 + PR |
| 通常リリース | 機能追加・変更 | 開発リード + QA 担当 | GitHub PR レビュー |
| インフラ変更（軽微） | 設定値変更 | インフラリード 1 名 | GitHub PR（Terraform） |
| インフラ変更（重大） | VPC・スキーマ変更 | インフラリード + システムマネージャー | 変更管理チケット + PR |
| DB マイグレーション | スキーマ変更 | 開発リード + DBA | GitHub PR + 手動確認 |

---

## 7. セキュリティ運用

### 7.1 アクセス管理

#### IAM 最小権限原則

- ECS タスクロール: 必要なサービス（S3 / Secrets Manager / CloudWatch）のみ許可
- 開発者: 本番環境への直接アクセス不可（ステージング環境は可）
- 本番アクセス: 承認制（緊急時 Break Glass 手順を別途定義）

```bash
# 本番環境の ECS Exec（緊急デバッグ時のみ。承認必須）
aws ecs execute-command \
  --cluster cargo-tracker-cluster \
  --task <TASK-ARN> \
  --container cargo-tracker-app \
  --interactive \
  --command "/bin/sh"
# 実行後は必ず監査ログに記録すること
```

#### Secrets Manager 管理

```bash
# シークレット一覧確認
aws secretsmanager list-secrets \
  --filters Key=name,Values=cargo-tracker \
  --query 'SecretList[*].{Name:Name,LastChanged:LastChangedDate}' \
  --output table

# 本番シークレットのローテーション（90 日ごとに手動実行）
aws secretsmanager rotate-secret \
  --secret-id cargo-tracker/db-credentials
```

### 7.2 パッチ管理

| 対象 | 頻度 | 方法 | 承認フロー |
|---|---|---|---|
| アプリケーション依存ライブラリ | 月次（Critical は即時） | Dependabot PR + CI | 通常リリースフロー |
| Docker ベースイメージ | 月次 | ECR 再ビルド + デプロイ | 通常リリースフロー |
| RDS マイナーバージョン | 四半期 | AWS コンソール / CLI | インフラ変更フロー |
| RDS メジャーバージョン | 年次（EOL 前） | Blue/Green デプロイ | 重大インフラ変更フロー |

```bash
# RDS エンジンバージョン確認（アップデート対象がないか確認）
aws rds describe-db-instances \
  --db-instance-identifier cargo-tracker-db \
  --query 'DBInstances[0].{Engine:Engine,Version:EngineVersion,PendingModification:PendingModifiedValues}'
```

### 7.3 セキュリティインシデント対応

| インシデント種別 | 初期対応 | エスカレーション |
|---|---|---|
| 不正アクセス検知 | 対象 IAM ユーザー・ロールを即時無効化 | セキュリティ担当 + システムマネージャー |
| データ漏洩疑い | ECS サービス停止 + 通信遮断 | CISO + 法務 |
| DDoS 攻撃 | AWS WAF ルール追加 + AWS Shield 確認 | インフラリード |
| 脆弱性発覚（Critical） | 24 時間以内にパッチ適用計画を策定 | 開発リード + セキュリティ担当 |

---

## 8. キャパシティ管理

### 8.1 ECS Auto Scaling 設定

| メトリクス | スケールアウト条件 | スケールイン条件 | クールダウン |
|---|---|---|---|
| CPU 使用率 | 70% 以上・3 分継続 | 30% 以下・5 分継続 | 300 秒 |
| メモリ使用率 | 80% 以上・3 分継続 | 40% 以下・5 分継続 | 300 秒 |
| ALB リクエスト数/タスク | 1,000 req/min を超過 | 300 req/min 以下 | 300 秒 |

**スケーリング範囲**: min 2 タスク / max 10 タスク

```bash
# Auto Scaling 設定確認
aws application-autoscaling describe-scaling-policies \
  --service-namespace ecs \
  --resource-id service/cargo-tracker-cluster/cargo-tracker-service

# 手動スケールアウト（緊急時）
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --desired-count 5
```

### 8.2 容量計画

**月次レビュー観点**:

- ECS タスク数のピーク・平均の推移（max 10 の 70% = 7 タスクを超えたら増枠検討）
- RDS 接続数の推移（上限の 70% を超えたら接続プール設定を見直し）
- RDS ストレージ使用量（80% を超えたらストレージ拡張を計画）
- CloudWatch Logs のストレージコスト（保持期間の見直し）

```bash
# ECS タスク数の推移（過去 7 日）
aws cloudwatch get-metric-statistics \
  --namespace ECS/ContainerInsights \
  --metric-name RunningTaskCount \
  --dimensions Name=ClusterName,Value=cargo-tracker-cluster \
  --start-time $(date -d "7 days ago" --iso-8601=seconds) \
  --end-time $(date --iso-8601=seconds) \
  --period 3600 \
  --statistics Maximum Average \
  --output table

# RDS ストレージ使用量確認
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name FreeStorageSpace \
  --dimensions Name=DBInstanceIdentifier,Value=cargo-tracker-db \
  --start-time $(date -d "1 day ago" --iso-8601=seconds) \
  --end-time $(date --iso-8601=seconds) \
  --period 3600 \
  --statistics Minimum \
  --output table
```

### 8.3 コスト最適化

| 施策 | 効果 | 実施タイミング |
|---|---|---|
| ECS Spot インスタンス（ステージング） | ~70% コスト削減 | ステージング構築時 |
| RDS 本番外は夜間停止 | ~60% コスト削減 | ステージング適用済み |
| ECR ライフサイクルポリシー（30 日超古いイメージ削除） | ストレージコスト削減 | 設定済み |
| CloudWatch Logs 保持期間最適化 | 不要なログコスト削減 | 月次レビュー時 |

---

## 9. 付録

### 9.1 運用チェックリスト

#### 日次チェックリスト

```markdown
## 日次運用チェック（YYYY-MM-DD）

### システム状態

- [ ] ECS HealthyHostCount: min 2 以上
- [ ] CloudWatch アラーム: ALARM 状態なし
- [ ] HTTP 5xx エラー率: 1% 未満
- [ ] RDS 自動バックアップ: 完了

### 確認コマンド実行済み

- [ ] aws ecs describe-services（ECS 状態）
- [ ] aws cloudwatch describe-alarms（アラーム状態）
- [ ] aws rds describe-db-snapshots（バックアップ状態）

### 異常・特記事項

（なし / 詳細を記載）

### 確認者

氏名:          確認時刻:
```

#### 月次チェックリスト

```markdown
## 月次運用チェック（YYYY-MM）

### セキュリティ

- [ ] セキュリティパッチ適用完了（または適用計画策定済み）
- [ ] IAM アクセス権限棚卸完了
- [ ] Secrets Manager ローテーション確認

### バックアップ

- [ ] RDS リストアテスト完了（所要時間: XX 分）
- [ ] テスト結果: RTO 目標（30 分）内 / 超過

### 容量・性能

- [ ] ECS タスク数ピーク確認（最大: X タスク）
- [ ] RDS ストレージ使用率確認（使用率: XX%）
- [ ] SLA/SLO 達成状況確認（稼働率: XX.XX%）

### コスト

- [ ] AWS コスト確認（予算比: +/-XX%）

### 作成者・確認者

作成者:        確認者:        実施日:
```

### 9.2 連絡先テンプレート

```markdown
## 障害連絡先一覧（機密情報のため別途管理）

### オンコール担当（輪番）

| 週 | 担当者 | 連絡先 |
|---|---|---|
| 第 1 週 | 担当 A | [電話番号] |
| 第 2 週 | 担当 B | [電話番号] |
| 第 3 週 | 担当 C | [電話番号] |
| 第 4 週 | 担当 D | [電話番号] |

### エスカレーション先

| レベル | 担当 | 連絡方法 |
|---|---|---|
| L2: インフラリード | [氏名] | [電話 / Slack] |
| L2: 開発リード | [氏名] | [電話 / Slack] |
| L3: システムマネージャー | [氏名] | [電話] |
| セキュリティ担当 | [氏名] | [電話 / メール] |

### 外部連絡先

| 対象 | 連絡先 | 備考 |
|---|---|---|
| AWS サポート | https://console.aws.amazon.com/support | Business サポートプラン |
| PagerDuty | https://[テナント].pagerduty.com | サービス: cargo-tracker |
```

### 9.3 Terraform 管理リソース一覧

| リソース | Terraform モジュール | State ファイル |
|---|---|---|
| VPC / サブネット / SG | `ops/terraform/modules/network` | S3: `cargo-tracker-tfstate/network` |
| ECS クラスター / サービス | `ops/terraform/modules/ecs` | S3: `cargo-tracker-tfstate/ecs` |
| RDS インスタンス | `ops/terraform/modules/rds` | S3: `cargo-tracker-tfstate/rds` |
| ALB / ターゲットグループ | `ops/terraform/modules/alb` | S3: `cargo-tracker-tfstate/alb` |
| CloudWatch アラーム / ダッシュボード | `ops/terraform/modules/monitoring` | S3: `cargo-tracker-tfstate/monitoring` |
| IAM ロール / ポリシー | `ops/terraform/modules/iam` | S3: `cargo-tracker-tfstate/iam` |

```bash
# Terraform State の確認
aws s3 ls s3://cargo-tracker-tfstate/ --recursive

# Terraform でのドリフト検出
cd ops/terraform
terraform plan -out=tfplan
terraform show -json tfplan | jq '.resource_changes[] | select(.change.actions != ["no-op"])'
```

### 9.4 用語集

| 用語 | 定義 |
|---|---|
| SLA | Service Level Agreement。顧客と合意した稼働率（99.9%） |
| SLO | Service Level Objective。内部目標稼働率（99.95%） |
| RTO | Recovery Time Objective。障害発生から復旧までの目標時間 |
| RPO | Recovery Point Objective。障害発生時に許容できるデータ損失範囲 |
| HealthyHostCount | ALB が正常と判断しているターゲット（ECS タスク）数 |
| Rolling Update | 旧バージョンのタスクを順次新バージョンに置き換えるデプロイ方式 |
| Break Glass | 緊急時に通常アクセス制限を超えた操作を行う手順（証跡必須） |
| PITR | Point-in-Time Recovery。任意の時点への RDS データ復元機能 |
