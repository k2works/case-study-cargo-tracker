# trackingms 公開トークン Secret 自動回転（ADR-0021 / IT9 A2.3）

trackingms の公開追跡照会 JWT 署名 secret を AWS Secrets Manager + Lambda Rotation で四半期ごとに自動回転する Terraform IaC。

## 構成

- **AWS Secrets Manager Secret** (`tracking/public-token`): AWSCURRENT / AWSPREVIOUS 段階管理
- **Lambda Function** (`tracking-public-token-rotation`, Python 3.12): AWS 標準 4 ステップ rotation
- **Secret Rotation**: 90 日サイクル（四半期）
- **IAM Role**: 最小権限（secretsmanager + CloudWatch Logs）

## 前提

- AWS CLI 設定済み（`aws configure` 済み、適切な IAM Role でログイン）
- Terraform >= 1.6.0
- Python 3.12（Lambda Runtime）

## 適用手順

```bash
cd ops/terraform/tracking-token-rotation
terraform init
terraform plan
terraform apply
```

`terraform apply` 後、自動的に AWSCURRENT 段階の secret が生成される（初回 rotation 実行）。

## trackingms 側の設定

`application-heroku.yml` または Heroku Config Vars に以下を設定:

```yaml
tracking:
  public-token:
    provider: aws-secrets-manager
    aws:
      secret-id: tracking/public-token
      region: ap-northeast-1
    refresh-rate-ms: 300000
```

trackingms 起動時に `AwsSecretsManagerTrackingTokenSecretProvider` が AWSCURRENT を取得し、5 分ごとに refresh する。Lambda rotation 後、最大 5 分以内に新シークレットで発行が開始される。

## 検証

```bash
# Secret の確認
aws secretsmanager describe-secret --secret-id tracking/public-token

# 手動 rotation トリガ
aws secretsmanager rotate-secret --secret-id tracking/public-token

# CloudWatch Logs で Lambda 実行ログを確認
aws logs tail /aws/lambda/tracking-public-token-rotation --follow
```

## LocalStack での開発検証

A2.4（IT9）で LocalStack 統合テストを追加予定。LocalStack pro 版（Lambda 機能含む）で本構成をエミュレートし、Terraform localstack provider を使って `terraform apply` で動作確認する想定。

## 削除

```bash
terraform destroy
```

`recovery_window_in_days = 7` のため、削除後 7 日間は復元可能。

## 関連

- ADR-0021: AWS Secrets Manager + Lambda 自動回転（IT9 で実装）
- ADR-0013: 公開追跡照会の時限署名トークン（IT6 確立、IT8 で四半期ローテーション基盤整備）
- iteration_plan-9.md A2: AWS Secrets Manager 統合
