# 0018 Billing Context にメール送信用 `MailNotificationPort` を導入する

US23 精算処理で荷主向けに発行する 3 種のメール通知 (支払発行 / 入金確認 / 期限超過) を、Billing Context の出力ポート `MailNotificationPort` 経由で送信する。IT8 では暫定実装としてログ出力のみ (`LoggingMailNotificationAdapter`)、IT9 以降で Pekko Mail / AWS SES 等を連携する。

日付: 2026-06-24

## ステータス

2026-06-24 承認・適用済 (IT8 タスク 2.9)。

## コンテキスト

US23 (ADR 0019 案 B) の `BillingCommandService` には以下の 3 メソッドがあり、それぞれ「Booking Context への通知ログ記録」と「荷主向けメール送信」の 2 つの副作用を持つ:

- `issuePayment` → 支払発行通知メール (期日 + 入金参照コード)
- `confirmPayment` → 入金確認通知メール
- `detectOverdue` → 期限超過警告メール

**Booking Context の `notification_log` テーブルへの記録は監査用 + 社内記録**であり、これは BookingPublicApi 経由で実装済。一方、**荷主へ実際にメールを届ける副作用は別ポート**として切り出す必要がある (責務分離)。

### IT8 スコープ

- 実メール送信は IT8 では実装しない（IT9 以降）
- 通知ログ記録と同期させる必要はある (UI 操作後にメール送信されたという業務イメージは維持)

### IT9 以降の選択肢

| 候補 | メリット | デメリット |
| :--- | :--- | :--- |
| Pekko Mail (JavaMail wrap) | Pekko/Akka エコシステムに統合、設定簡単 | SES と比較し送達率劣る、SPF/DKIM 別途設定要 |
| AWS SES | 高送達率、AWS インフラ統合 | SDK 依存追加、SES sandbox 解除手続要 |
| 外部 SaaS (SendGrid / Mailgun) | テンプレ機能・分析機能充実 | API キー管理・コスト |

## 決定

**`MailNotificationPort` trait を Billing Context の `domain.model.ports` 配下に新設する。**

- IT8 では `LoggingMailNotificationAdapter` (Play Logger による INFO 出力のみ) を `infrastructure.mail` 配下に配置
- Module で `bind(MailNotificationPort).to(LoggingMailNotificationAdapter)`
- `BillingCommandService` が Port を Inject、3 メソッド (`issuePayment` / `confirmPayment` / `detectOverdue`) で対応する `sendXxx` を呼び出し
- 送信失敗は `Either[String, Unit]` で返すが、IT8 では **ベストエフォート扱い** (失敗してもメインフロー継続)
- IT9 以降で Adapter 実装を Pekko Mail / AWS SES 等に差し替える

### `LoggingMailNotificationAdapter` 出力例

```
[MAIL:PaymentRequested] bookingId=BK-000001 invoice=INV-000001 dueDate=2026-10-31 ref=PAY-REF-001 amount=15300
[MAIL:PaymentConfirmed] bookingId=BK-000001 invoice=INV-000001 paidAt=2026-10-15T09:00:00Z amount=15300
[MAIL:OverdueAlert] bookingId=BK-000001 invoice=INV-000001 dueDate=2026-10-31 amount=15300
```

これにより IT8 完了時点で「メール送信のポイントが論理的に明示されている」状態が達成され、IT9 で Adapter を差し替えるだけで本番運用に移行可能。

## 影響

### IT8 内変更

- `billing/domain/model/ports/MailNotificationPort.scala` 新設
- `billing/infrastructure/mail/LoggingMailNotificationAdapter.scala` 新設
- Module.scala に bind 追加
- BillingCommandService コンストラクタに `mailPort: MailNotificationPort` 追加、3 メソッドで `sendXxx` 呼出し
- BillingCommandServiceSpec に `NoopMail` テストダブル追加、16 件 Green

### IT9 以降

- `PekkoMailNotificationAdapter` または `AwsSesMailNotificationAdapter` の実装
- `application.conf` に SMTP / SES 設定追加
- Module bind を差し替え (テスト時は LoggingMailNotificationAdapter を継続利用)
- メール本文テンプレート (Twirl) の追加検討
- 失敗時のリトライ / Dead Letter Queue の設計 (ADR 新規起票候補)

### 帰結

- **責務分離**: 通知ログ (監査) とメール送信 (荷主体験) が明確に分離
- **段階的移行**: IT8 はメイン業務ロジックに集中、IT9 でメール基盤に集中という分業が可能
- **テスト容易性**: Port パターンにより NoopMail で全ケース検証可能
- **将来制約**: 配信保証 / 順序保証 / 一元監視は IT9 で別途検討

## コンプライアンス

- BillingCommandServiceSpec で `sendXxx` 呼出ログが期待回数記録されることを検証 (NoopMail バッファ確認、IT8 2.10 で追加予定)
- IT9 で実 Adapter 切替時の統合テスト追加
- Port が `domain.model.ports` 配下、Adapter が `infrastructure.mail` 配下にあることを ArchUnit ルールで確認 (将来追加)

## 備考

- 起票者: AI Agent (IT8 タスク 2.9)
- 関連 ADR: 0017 (BookingPublicApi)、0019 (Payment 集約方針)
- 関連 commit: IT8 2.9 (本 ADR と同時)
