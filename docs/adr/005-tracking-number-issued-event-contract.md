# ADR-005: TrackingNumberIssuedEvent のマイクロサービス間契約管理方針

bookingms と trackingms の両方に同名の `TrackingNumberIssuedEvent` record が存在する構造を許容し、独立デプロイ時のスキーマ乖離リスクをドキュメント化して管理する方針を採用します。

日付: 2026-05-09

## ステータス

承認済み

## コンテキスト

IT6 レビュー（xp-architect / xp-technical-writer）で以下の問題が指摘された。

- `TrackingNumberIssuedEvent` が `bookingms.domain.events` と `trackingms.domain.events` の両方に同名の record として重複定義されている
- 独立デプロイ時に一方のスキーマが変更されると、もう一方が古いフォーマットを受信して破壊的障害が発生するリスクがある
- 契約の変更管理方針が明文化されていない

現在のイベントスキーマ:

```java
// trackingms（発行側）
record TrackingNumberIssuedEvent(String bookingId, String trackingNumber) {}

// bookingms（購読側）
record TrackingNumberIssuedEvent(String bookingId, String trackingNumber) {}
```

選択肢として以下が考えられた。

1. **独立コピー管理**: 現状維持。各マイクロサービスが独立したコピーを持つ
2. **共有ライブラリ（共有カーネル）**: `event-contracts` モジュールを作成し両者が依存する
3. **Consumer-Driven Contract（CDC）テスト**: Pact 等のツールで契約テストを実装する
4. **スキーマレジストリ**: Confluent Schema Registry 等で中央管理する

## 決定

**独立コピー管理 + 変更プロセスの明文化** を採用する。

### 理由

- 現在のフィールド数が 2 件（`bookingId`, `trackingNumber`）と少なく、共有ライブラリのオーバーヘッドが見合わない
- MVP フェーズ（v0.1.x）ではマイクロサービスの独立デプロイは行わず、モノレポ内で一括デプロイする
- 共有ライブラリ化はサービス間の結合度を高め、将来の独立進化を阻害するリスクがある
- Pact 等の CDC テストは Phase 2 以降（独立デプロイが現実になった時点）で導入を検討する

### 変更プロセス（コンベンション）

`TrackingNumberIssuedEvent` のスキーマを変更する場合は以下の手順に従う。

1. **後方互換フィールド追加のみ許可**: 既存フィールドの削除・型変更・名前変更は禁止
2. **両サービス同時更新**: 発行側（trackingms）と購読側（bookingms）の record を同一 PR で更新する
3. **統合テストによる検証**: 変更後に `TrackingNumberIssuedEventListenerTest`（Testcontainers）でエンドツーエンドのメッセージ疎通を確認する
4. **本 ADR のフィールド一覧を更新**: スキーマが変更された場合は本 ADR に変更履歴を追記する

### 現在の契約定義

| フィールド | 型 | 必須 | 説明 |
|------------|-----|------|------|
| `bookingId` | `String` | ✓ | 予約 ID（`BK-XXXXXX` 形式） |
| `trackingNumber` | `String` | ✓ | 追跡番号（`TRK-XXXXXX` 形式） |

### Phase 2 以降の移行計画

独立デプロイが現実となった場合（Phase 2 / v0.2.x 以降）は以下を検討する。

- **Consumer-Driven Contract テスト（Pact）**: `bookingms` が `trackingms` に期待する契約を Pact ファイルで定義し CI で検証する
- **スキーマレジストリ**: フィールド増加時に Avro / Protobuf + Confluent Schema Registry を採用する

## 結果

- MVP フェーズにおける開発速度を維持しつつ、スキーマ変更時の安全な手順を確立できる
- 変更プロセスがドキュメント化されることで、「暗黙の契約」から「明示的な契約」に移行する
- Phase 2 での CDC テスト導入の判断基準（独立デプロイの開始）が明確になる
- ただし、モノレポの一括デプロイを維持する限り、スキーマ乖離リスクはゼロである

## 更新履歴

| 日付 | 内容 | 担当 |
|------|------|------|
| 2026-05-09 | 初版作成（IT6 コードレビュー #4 対応） | - |
