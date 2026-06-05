# ADR-0018: 通知アダプタ（NotificationAcl 実装）選定

trackingms / billingms の `NotificationAcl` は IT5 / IT7 では `LoggingNotificationAcl`（INFO ログ出力スタブ）で実装した。本番運用前に実メール / SMS 送信を行う具象実装を選定する必要がある。本 ADR では IT8 で採用する通知サービスと統合方式を確定する。

日付: 2026-06-05

## ステータス

提案中（IT8 着手時に確定）

## コンテキスト

### IT7 までの状況

- trackingms `NotificationAcl`: tracking issued / status changed / misrouted / exception registered / exception resolved / exception escalation の 6 メソッド
- billingms `NotificationAcl`: invoice issued / payment received / overdue の 3 メソッド
- 両者とも `LoggingNotificationAcl` で INFO/WARN ログ出力スタブ
- 業務上は荷主・荷受人・経理担当者・追跡管理者・管理職へのメール通知が要件
- iteration_plan-7.md / iteration_plan-5.md で「IT8 で SendGrid 統合」と記載済み

### 利用要件

| 観点 | 要件 |
|------|------|
| メール送信 | 必須（HTML / プレーンテキスト両対応）|
| SMS 送信 | IT9 以降で検討（現状不要）|
| テンプレート | 多言語対応（日本語 / 英語）|
| 配信メトリクス | 送信成功 / 失敗 / バウンスの可視化（Heroku metrics 連携）|
| escalation 経路 | 紛失例外（LOSS）は管理職向け別テンプレート |
| 配信遅延 | 平均 30 秒以内、業務に致命的でない |
| コスト | $20/月以下（IT7 段階の規模感）|

### 候補

| 候補 | メリット | デメリット |
|------|---------|-----------|
| **SendGrid** | Heroku Add-on（$15/月から）、Spring Boot Starter あり、テンプレート機能充実 | Twilio 傘下の独自仕様、SMTP より複雑 |
| **Amazon SES** | 安価（$0.10/1000 通）、AWS エコシステム親和 | Heroku + AWS の 2 アカウント管理、IT8 のクラウド方針未定 |
| **Postmark** | トランザクションメール特化、配信信頼性高い | コスト高め、テンプレート機能はベーシック |
| **生 SMTP**（Gmail SMTP / AWS SES SMTP） | 最も柔軟、ライブラリ不要 | 配信メトリクス自前実装、テンプレートエンジン別途必要 |

## 決定

**SendGrid を採用する**（IT8 で実装）。

### 方針

1. **SendGrid Add-on（Heroku）**: Starter プラン（$15/月、40,000 通/月）から開始。本番拡大時は Bronze（$29.95/月、100,000 通）に昇格
2. **Spring 統合**: `com.sendgrid:sendgrid-java` SDK を直接利用（Spring Boot Starter は公式提供なし、薄い `@Configuration` で `SendGrid` Bean を定義）
3. **テンプレート**: SendGrid Dynamic Templates を使用。テンプレート ID は `application.yml` の `notification.sendgrid.templates.*` で管理
4. **実装クラス**: `SendGridNotificationAcl` を `infrastructure/outboundservices/notification/` に配置。`@ConditionalOnProperty(name = "notification.adapter", havingValue = "sendgrid")` で `LoggingNotificationAcl` と切替可能に
5. **失敗時の振る舞い**: 送信失敗時は WARN ログ + Micrometer counter `notification.sent{status=failure,channel=sendgrid}` で監視。ビジネスフロー（集約遷移）は止めない（fire-and-forget）

### 代替案の評価

| 代替案 | 採用しない理由 |
|--------|----------------|
| **Amazon SES** | IT8 段階で AWS アカウント運用方針未確定。コスト優位は規模拡大後 |
| **Postmark** | テンプレート機能が弱く、escalation 経路の HTML テンプレート分岐が運用負担 |
| **生 SMTP** | 配信メトリクス自前実装の運用コスト。テンプレートエンジン（Thymeleaf 等）を別途管理 |

### 実装計画（IT8）

```kotlin
// build.gradle
implementation 'com.sendgrid:sendgrid-java:4.10.x'
```

```yaml
# application-heroku.yml
notification:
  adapter: sendgrid
  sendgrid:
    apiKey: ${SENDGRID_API_KEY}
    fromEmail: noreply@cargo-tracker.example.com
    templates:
      invoiceIssued: d-aaaa1111...
      paymentReceived: d-bbbb2222...
      overdue: d-cccc3333...
      trackingIssued: d-dddd4444...
      statusChanged: d-eeee5555...
      exceptionRegistered: d-ffff6666...
      exceptionResolved: d-gggg7777...
      exceptionEscalation: d-hhhh8888...
```

```java
@Component
@ConditionalOnProperty(name = "notification.adapter", havingValue = "sendgrid")
public class SendGridNotificationAcl implements NotificationAcl {
    private final SendGrid sendGrid;
    private final NotificationProperties properties;
    private final Counter sentCounter;
    private final Counter failedCounter;
    // ...
}
```

```java
@Component
@ConditionalOnMissingBean(NotificationAcl.class)
public class LoggingNotificationAcl implements NotificationAcl {
    // IT7 既存実装、開発・テスト用デフォルト
}
```

### 受け入れテスト

- `SendGridNotificationAcl` 単体テスト: SendGrid client を WireMock で mock し、テンプレート ID と payload が期待通り送信されることを検証
- 統合テスト: `notification.adapter=sendgrid` で起動して LoggingNotificationAcl が生成されないことを確認
- 本番デプロイ前: SendGrid テスト送信機能で各テンプレートをマニュアル検証

## 影響

### 適用対象

- **trackingms `NotificationAcl`**: 6 メソッドすべてに SendGrid テンプレート ID をマッピング
- **billingms `NotificationAcl`**: 3 メソッドすべてに SendGrid テンプレート ID をマッピング
- **profile**: heroku / local-docker は SendGrid、local-h2 はデフォルト Logging

### 既存 ADR との関係

- **ADR-0009 cross-service Saga**: NotificationAcl 呼び出しは subscribing モードで処理し Saga には参加させない（fire-and-forget）
- **ADR-0012 集約発火型**: 通知 EventHandler は副作用列を持たないため本 ADR §3 フラグ列ガードは不要（fire-and-forget なら冪等性は通知側で重複許容）
- **本 ADR と相補**: 通知の配信失敗は業務フローを止めない設計（ADR-0011 ホワイトリストの精神を踏襲）

## 備考

- 著者: k2works（IT5 0.6 持ち越し → IT7 review 中持ち越し → IT8 着手時に確定）
- 関連: IT5 / IT7 review notification-stub
- IT8 で SendGrid Add-on のプロビジョニングは ops/scripts/heroku.js に追加
