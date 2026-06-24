# 0016 HandlingOrchestrator のトランザクション境界

`HandlingOrchestrator.register` は 4 ステップ（Handling 登録 / Tracking event 追記 / Booking 通知ログ / Claim 時 deliveryComplete）を逐次実行する。本 ADR では「これらを単一 DB トランザクションで囲むべきか、それともコンテキスト境界を尊重して分散させ Outbox + Domain Events で結果整合性を担保すべきか」を決定する。

日付: 2026-06-24

## ステータス

2026-06-24 承認・適用 (IT8 タスク 0.4)。当面は **「単一 DB.localTx + ベストエフォート補償ログ」案** を採用、Phase 5 で要件が変わった場合に Outbox 化を再評価する申し送り。

## コンテキスト

### 現在の実装

`HandlingOrchestrator.register` は次の 4 ステップを `for ... yield` でチェーンしている (`apps/cargo-tracker/app/cargotracker/handling/application/commandservices/HandlingOrchestrator.scala`)。

1. `handlingCommandService.register(...)` — Handling Context の `handling_activity` テーブル INSERT
2. `trackingPort.recordEvent(...)` — Tracking Context の `tracking_activity_event` テーブル INSERT + `tracking_activity.version` UPDATE
3. `bookingPort.logHandling(...)` — Booking Context の `notification_log` テーブル INSERT
4. (eventType == "Claim" 時) `bookingPort.completeDelivery(...)` — Booking 集約状態遷移 + `cargo_booking.status` UPDATE + DeliveryCompleted 通知ログ

各ポート実装 (`ScalikeJdbcXxxRepository`) は内部で **独立した** `DB.localTx { ... }` を開く。すなわち上記 4 ステップは **4 つの別々のトランザクション**で実行され、ステップ 3 失敗時はステップ 1 / 2 は既にコミット済み状態となる。

### 既存テスト保護範囲

- HandlingOrchestrator のユニットテスト (`HandlingOrchestratorSpec`) は In-Memory ポート実装でハッピーパス + ステップ 3 失敗時の Left 返却を確認
- ステップ 1 成功・ステップ 3 失敗時の **永続層の整合性** （Handling は INSERT 済 / Booking 通知ログは未記録）は IT7 までで検証していない

### IT7 レビューでの指摘

- **H4 (xp-architect)**: 「`recordEvent` 成功後 `logHandling` 失敗時のデータ不整合リスク。単一 `DB.localTx` OR Outbox + Domain Events の選択を ADR 0016 で記録」
- **T2 (Try)**: 「Handling から Tracking / Booking への副作用順序を変更する余地」

### 業務影響評価

| ステップ失敗 | 業務影響 | 復旧難易度 |
| :--- | :--- | :--- |
| ステップ 1 失敗 | Handling 自体が未記録 → そのまま再実行で OK | 低 |
| ステップ 2 失敗（ステップ 1 後）| Handling 記録済、Tracking 履歴欠落 → ユーザーが追跡画面で「荷役は実施されたが反映されない」と感知 | 中 |
| ステップ 3 失敗（ステップ 1 / 2 後）| Handling・Tracking は反映済、通知ログのみ欠落 → 内部監査時の証跡欠落 | 低（業務影響は軽微）|
| ステップ 4 失敗（Claim 時）| 配送完了通知のみ欠落 → ユーザー / 監督者への通知漏れ | 中 |

最も重要なのは **ステップ 2 失敗時の「Handling 記録済だが Tracking 未反映」状態** であり、ここを保護したい。

## 決定

**「単一 DB.localTx + ベストエフォート補償ログ」案 (案 A) を採用する。** Outbox + Domain Events 案 (案 B) は採用しない。

### 採用方針

1. **HandlingOrchestrator に単一の `DB.localTx { implicit session => ... }` 境界を導入する** (IT8 0.4 後続 / IT9 実装候補):
   - 各 `ScalikeJdbcXxxRepository.save` / `update` メソッドが `implicit DBSession` を受け取れるよう shipper / cargo / handling / tracking / booking 全 Repository を改修
   - HandlingOrchestrator が `DB.localTx { implicit s => stepA(); stepB(); stepC(); stepD() }` で 4 ステップを 1 TX に統合
   - ステップ間で例外が発生したら `DB.localTx` が rollback し、 4 テーブル全てが整合状態に戻る
2. **通知ログ (ステップ 3) はベストエフォート扱いとし、本トランザクションに含めるが失敗時のリカバリ動線は要件外** とする (IT8 業務観点では通知ログは監査用 + ユーザー通知は別経路で送信予定のため)
3. **本 ADR は方針決定のみ**。実装は IT8 0.4 で本 ADR 起票 → IT8 中後期 (タスク 1.x / 2.x 完了後の余力枠) または IT9 で実施。IT8 着地優先により実装を見送る場合は IT9 申し送りとする
4. **Phase 5 で他 Context (Estimation / Settlement) との連携が増えた場合**、改めて Outbox + Domain Events 案 (ADR 新規) を検討する

### 案 B (Outbox + Domain Events) を不採用とした理由

| 観点 | 案 A 採点 | 案 B 採点 |
| :--- | :---: | :---: |
| **実装コスト**: IT8 スコープ (9 SP / 2 週) で吸収可能か | ◯ 各 Repository を `implicit DBSession` 受取に拡張するだけ (IT8 中期 1-2 日) | × Outbox テーブル新設 / Pekko Scheduler / リトライ / 重複排除 / イベント順序保証で 5+ 日 |
| **業務要件適合性**: 結果整合性は許容されるか | ◯ 荷役登録は同期的に完了通知する業務上、即時整合の方が UX 良好 | △ 結果整合は数秒〜数分のラグ、ユーザー画面更新タイミングが煩雑 |
| **境界尊重 (DDD ヘキサゴナル)** | △ Handling Context の TX が Tracking / Booking テーブルを跨ぐ点で純粋ではない | ◯ 各 Context は独立 TX、Domain Events で疎結合 |
| **障害復旧の単純さ** | ◯ Atomic、 rollback で済む | × Outbox 詰まり / リトライ失敗 / 順序逆転の運用負荷 |
| **将来拡張性 (3+ Context 連携)** | △ Handling Orchestrator の責務が肥大化する余地 | ◯ 横展開しやすい |

IT8 スコープ + 業務要件適合性を最重要視し、**案 A 採用が短期的にも長期的にも妥当** と判断した。境界尊重の懸念は「HandlingOrchestrator は明示的な Cross-Context Orchestrator であり、本来そのために存在する」という設計意図で吸収する。

### 単一 DB.localTx 案の実装詳細 (IT8 0.4 後続 / IT9 候補)

```scala
// 改修後のスケッチ
@Singleton
class HandlingOrchestrator @Inject() (
    handlingCommandService: HandlingCommandService,
    trackingPort: TrackingLookupPort,
    bookingPort: BookingNotificationPort
):
  def register(input: RegisterHandlingFlowInput): Either[String, Unit] =
    DB.localTx { implicit session =>
      val result = for
        bookingId <- trackingPort.findBookingIdByTrackingNumber(...).toRight(...)
        _ <- handlingCommandService.register(...)(session) // implicit DBSession
        _ <- trackingPort.recordEvent(...)(session)
        _ <- bookingPort.logHandling(...)(session)
        _ <- if input.eventType == "Claim" then bookingPort.completeDelivery(...)(session) else Right(())
      yield ()
      result match
        case Left(msg) => throw RollbackException(msg) // rollback 強制
        case Right(()) => ()
      result
    }
```

- `RollbackException` は Scala 例外として `DB.localTx` を rollback させるためのマーカー
- ポート定義に `(implicit DBSession)` を伝播させるのは **Handling 配下のヘキサゴナル境界を多少緩める** トレードオフ。引き換えに 4 テーブルの整合性が確実に取れる
- もしくは ScalikeJDBC の `DB.localTxWithConnection` を使い、外部 Connection を渡せる方式に変更する案もある (IT8 0.4 後続実装時に判断)

## 影響

### IT8 内変更

- 本 ADR を起票・承認すれば 0.4 タスクは完了マーク可能
- 実装 (`HandlingOrchestrator` + 各 Repository の `implicit DBSession` 拡張) は IT8 後半余力で対応、無理ならば IT9 0.x 申し送り

### 既存ドキュメント

- `architecture_backend.md` の「トランザクション境界」セクション (existing) に本 ADR へのリンクを追記する (IT8 0.12 設計ドキュメント反映時)
- `domain-model.md` Handling Context セクションに「HandlingOrchestrator は Cross-Context Orchestrator として 単一 TX 境界を持つ」と記載 (IT8 0.12 で実施)

### テスト戦略

- IT8 0.4 後続実装時に `HandlingOrchestratorIntegrationSpec` (Testcontainers PostgreSQL) を新設し、「ステップ 3 で意図的に失敗させたとき Handling + Tracking テーブル両方がロールバックされる」ことを検証
- ステップ 4 (Claim 時 completeDelivery) も同様に rollback 動作を検証

### 帰結

- **整合性確保**: ステップ 2 失敗時のデータ不整合が構造的に消滅
- **DDD 境界の妥協**: HandlingOrchestrator が他 Context テーブルを跨ぐ TX を持つことを許容
- **将来制約**: Phase 5+ で Context 数が増えた場合、本決定は再評価対象。ADR 新規起票で superseded を記録

## コンプライアンス

- `HandlingOrchestrator.scala` のコメントに本 ADR への参照を残す (IT8 0.4 後続実装時)
- 統合テスト (`HandlingOrchestratorIntegrationSpec`) で rollback 動作を検証
- ArchUnit ルール: `HandlingOrchestrator` のみが Handling Context 外 Repository / Port の `DBSession` を引き回せる (他 application 層は単一 Context 内で完結)

## 備考

- 起票者: AI Agent (IT8 タスク 0.4、IT7 H4 / T2 解消)
- 関連 ADR: 0017 (BookingPublicApi、ACL 越境解消 = ステップ 4 の completeDelivery 呼び出し改善)、0014 (Snapshot ADT)、0007 (楽観ロック Either API)
- 関連レビュー: `docs/review/it7_implementation_review_20260623.md` H4 / T2
- 実装着地時期は IT8 / IT9 のいずれか、後述の余力枠次第。IT8 完了時点で実装未着手であれば IT9 申し送り
