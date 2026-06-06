# ADR-0021: AWS Secrets Manager + Lambda 自動回転（trackingms 公開トークン）

IT8 T1.6 で trackingms の公開追跡照会トークン用 secret について、四半期ローテーション対応の `TrackingTokenSecretProvider` ポート抽出と `StaticTrackingTokenSecretProvider` 実装（環境変数 + 旧 secret フォールバック）を完了した。本 ADR では IT9 で実装する AWS Secrets Manager + Lambda 自動回転の設計を確定する。

日付: 2026-06-05

## ステータス

採用済み（実装完了）

- 2026-06-05: 提案 / IT8 終了時点
- 2026-06-06: IT9 で全範囲を実装完了（A2.1〜A2.4）。`AwsSecretsManagerTrackingTokenSecretProvider`（AWSCURRENT + AWSPREVIOUS 取得 + `@Scheduled` 5 分間隔 refresh）、AWS SDK secretsmanager 2.30.27 統合、LocalStack Testcontainers 統合テスト、Lambda rotation Function（Python 3.12 / AWS 標準 4 ステップ）+ Terraform IaC（90 日サイクル）をすべて実装
- 2026-06-06: マルチパースペクティブレビューで H9（rotation 失敗時 PagerDuty/Slack 通知未定義）/ L3（Micrometer メトリクス化）/ H7（LocalStack IT の CI 隔離確認）が IT10 持ち越しとして識別済み

## コンテキスト

### IT8 終了時点のローテーション基盤

- `TrackingTokenSecretProvider` interface（{@code activeSigningKey} + {@code verifyingKeys}）
- `StaticTrackingTokenSecretProvider`（{@code tracking.public-token.secret} + {@code previous-secret} を環境変数から取得）
- `TrackingTokenService` は複数キー試行で旧 secret 署名トークンも検証可能（四半期ローテーション期間中の互換性確保）
- application.yml に `TRACKING_PUBLIC_TOKEN_PREVIOUS_SECRET` 環境変数の placeholder

これにより手動ローテーション（新 secret 生成 → 旧 secret を previous に降格 → 1 四半期経過後に previous 削除）は可能。但し:

- 経理 / 運用担当者の手作業が必要（Heroku Config Vars の更新 + 再デプロイ）
- 過去 1 サイクルの secret しか保持できない（複数バージョン保持不可）
- 自動化されておらず、ローテーション忘れ / タイミングずれのリスク

### IT9 で導入する AWS Secrets Manager + Lambda 自動回転

decoded benefits:

1. **自動回転**: Lambda で 90 日ごとに新 secret を生成し AWS Secrets Manager に登録
2. **AWSCURRENT / AWSPREVIOUS / AWSPENDING**: バージョン段階で複数 secret を保持、TrackingTokenService が両方で検証可能
3. **監査ログ**: AWS CloudTrail で secret アクセスログが残る
4. **暗号化**: AWS KMS による rest 暗号化、IAM Role による access 制御

## 決定

### 1. AWS SDK 依存追加

```gradle
// trackingms/build.gradle
implementation 'software.amazon.awssdk:secretsmanager:2.27.x'
```

### 2. AwsSecretsManagerTrackingTokenSecretProvider 実装

```java
@Component
@ConditionalOnProperty(name = "tracking.public-token.source", havingValue = "aws-secrets-manager")
public class AwsSecretsManagerTrackingTokenSecretProvider implements TrackingTokenSecretProvider {

    private final SecretsManagerClient client;
    private final String secretArn;
    private volatile SecretKey activeKey;
    private volatile List<SecretKey> verifyingKeys;

    @Scheduled(fixedDelay = 300_000) // 5 分ごとに再取得
    public void refresh() {
        GetSecretValueResponse current = client.getSecretValue(req -> req
                .secretId(secretArn).versionStage("AWSCURRENT"));
        SecretKey newActive = toKey(current.secretString());

        SecretKey previous = null;
        try {
            GetSecretValueResponse pre = client.getSecretValue(req -> req
                    .secretId(secretArn).versionStage("AWSPREVIOUS"));
            previous = toKey(pre.secretString());
        } catch (ResourceNotFoundException ex) {
            // 初回ローテーション前は AWSPREVIOUS が存在しない
        }

        this.activeKey = newActive;
        this.verifyingKeys = previous == null
                ? List.of(newActive)
                : List.of(newActive, previous);
    }
}
```

### 3. Lambda 自動回転

- Secret に rotation を有効化（90 日間隔）
- Lambda 関数（Python / Node.js）が以下を実行:
  1. AWSPENDING に新 secret（32 バイト以上のランダム）を作成
  2. AWSCURRENT を AWSPREVIOUS に降格、AWSPENDING を AWSCURRENT に昇格
  3. AwsSecretsManagerTrackingTokenSecretProvider が次回 refresh() で新旧両方を取得
- 90 日経過後、AWSPREVIOUS は自動削除される（互換性チェックなしで切り替わる）

### 4. 設定切替

- `tracking.public-token.source=env`（デフォルト）: 既存の StaticTrackingTokenSecretProvider
- `tracking.public-token.source=aws-secrets-manager`: AwsSecretsManagerTrackingTokenSecretProvider
- @ConditionalOnProperty + @ConditionalOnMissingBean で IT9 持ち越しの adapter パターン

### 5. ローカル / CI 環境

- AWS Secrets Manager を CI で使うコストが高いため、LocalStack で代替
- 単体テストは AwsSecretsManagerClient を mock 化
- 統合テストは Testcontainers + LocalStack で最低限のフロー確認

## 結果

### Positive

- secret の rest 暗号化（KMS）+ アクセス制御（IAM）+ 監査ログ（CloudTrail）が標準化
- ローテーションの自動化により手作業ミスを排除
- 過去 1 バージョンの旧 secret は AWS が管理するため、運用担当者の負担が減る
- IT8 で導入した `TrackingTokenSecretProvider` ポートにより、StaticTrackingTokenSecretProvider との切替が IT9 で最小コスト

### Negative

- AWS 依存により Heroku 単独デプロイ（ADR-0006）の自己完結性が崩れる
  - 緩和: 既定は env、本番は AWS の adapter パターン維持
- AWS Secrets Manager のコスト: $0.40 / secret / month + $0.05 / 10,000 API calls
  - 緩和: trackingms 1 instance につき 1 secret のみ、refresh は 5 分ごとなので影響微小
- Lambda の rotation スクリプトのテスト / メンテナンス
  - 緩和: AWS Secrets Manager template の rotation Lambda を流用

### Neutral

- IT9 の 1 ストーリーとして「AWS Secrets Manager 統合 + Lambda 自動回転」を切り出す（2-3SP 想定）
- ADR-0013（公開トークン）の AUDIENCE / claim 仕様は変更なし
- ADR-0006（Heroku デプロイ）に AWS 設定の追加章を起こす

## 関連 ADR

- [ADR-0006 Heroku デプロイ設定](0006-heroku-deployment-setup.md) — AWS 追加設定の参照
- [ADR-0013 公開追跡照会トークン](0013-public-tracking-token.md) — トークン仕様の基底
- [ADR-0020 決済機関 webhook](0020-payment-gateway-webhook.md) — 同じく外部依存の adapter パターン例

## 実装スケジュール

| イテレーション | 内容 |
|---------------|------|
| IT8（現行）| TrackingTokenSecretProvider ポート + StaticTrackingTokenSecretProvider 実装 + 四半期ローテーション基盤（previous-secret） |
| IT9 候補 | AwsSecretsManagerTrackingTokenSecretProvider 実装 + Lambda rotation 関数 + Testcontainers + LocalStack 統合テスト |
| IT10 候補 | 他 ms（authms / billingms 等）の secret も AWS Secrets Manager に統一 |
