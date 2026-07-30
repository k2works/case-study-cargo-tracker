# ADR-012: 通知記録の所有を共有アダプタに集約し、種別を型化して本文を持たせる

`notification_record` への書き込みを共有インフラの単一アダプタに集約し、通知種別を union 型で拘束し、通知本文（金額・期限・対応方針等）を保持できるようにする。Notification Context への分割は将来判断とする。

日付: 2026-07-30

## ステータス

承認済み

## コンテキスト

IT6 のレビューで、通知に関する複数の負債が指摘された。

- **所有者不在**（architect M2）: `notification_record` に Handling / Tracking / Booking の 3 BC がそれぞれ独自の Kysely アダプタで直接 insert しており、テーブルの所有 BC が定義されていない。スキーマ変更が 3 BC に波及する。
- **種別のマジックリテラル**（architect M2）: 通知種別（`STATUS_CHANGED` / `EXCEPTION_REPORTED` / `ESCALATION` / `EXCEPTION_REPORT` / `ROUTE_PROPOSED` / `BOOKING_CONFIRMED` / `BOOKING_CANCELLED` / `TRACKING_ISSUED`）が文字列リテラルで各 BC に散在し、typo が型で検知できない。
- **本文が届かない**（user H2）: `notification_record` は種別と宛先のみを保持し、対応報告の本文（新到着予定日・対応方針・補償方針）や精算の金額・期限といった、荷主の判断に必要な情報が一切載らない。IT7 の精算通知でも同じ欠落が生じる。

## 決定

1. **通知記録の書き込みを共有インフラの単一アダプタに集約する**。`shared/infrastructure/notification/`（新設）に `NotificationRecorder`（`record(bookingId, type, recipient, body)`）を置き、各 BC の通知ポート実装はこのアダプタへ委譲する。`notification_record` テーブルの所有は「共有インフラ」とし、3 BC の直接 insert を廃止する。
2. **通知種別を共有契約の union 型で拘束する**。`shared/contracts/notification-type.ts` に `NotificationType`（as const union）を定義し、全 BC がこれを参照する。マジックリテラルを排除する。
3. **`notification_record.body`（TEXT・NULL 許容）を追加する**。対応報告（新到着予定日・対応方針）、精算（請求金額・支払期限）、エスカレーション（例外種別・発生地）等の本文を人間可読な要約として記録する。実配信（メール本文）はこの body を素材とする。
4. **管理職エスカレーションの宛先を設定値化する**。固定リテラル `management@example.com` を設定（環境変数 / 定数）へ移し、実運用で監視される宛先に差し替え可能にする。
5. **Notification Context への BC 分割は将来判断とする**。現時点では独立 BC 化のコストが過剰であり、共有インフラアダプタへの集約で所有と重複を解消する。分割の要否は通知チャネルが増える（SMS・Webhook 等）時点で再判断する。

### 代替案

- **Notification を独立 BC として今分割**: イベント駆動の通知集約は堅牢だが、単一プロセス・単一チャネル（記録スタブ）の現状には過剰。却下（将来の移行先として留保）。
- **各 BC が直接 insert を継続**: 所有不在・重複・スキーマ波及の負債が残る。却下。

## 影響

### ポジティブ

- `notification_record` の書き込み口が 1 箇所になり、スキーマ変更の影響が局所化する
- 通知種別が型安全になり、本文設計により「記録はあるが届かない」ギャップが解消する

### ネガティブ

- 3 BC の既存通知アダプタ（recording-*-notification.service）を共有アダプタ委譲へ移行する必要がある
- body の内容設計（何を要約に載せるか）を種別ごとに決める必要がある

## コンプライアンス

- 通知種別が union 型で参照され、リテラル直書きが残っていないことをコードレビューで確認する
- body に本文が載ることを、対応報告・精算・エスカレーションの各テストで検証する

## 備考

- 著者: k2works
- 関連 ADR: ADR-005（イベント連携）、ADR-009（コミット後副作用）
- 関連: IT6 実装レビュー（architect M2 / user H2）、IT7 計画 タスク 1.2・注 8
