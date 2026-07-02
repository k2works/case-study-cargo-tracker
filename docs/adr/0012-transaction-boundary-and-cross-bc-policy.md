# 0012 トランザクション境界と Cross-BC 参照ポリシー

複数 Repository への書き込みを含む Application Command のトランザクション境界統合と、
BC 間 Domain 参照ルールの明文化

日付: 2026-07-02

## ステータス

採用 (2026-07-02、IT6 T5-03 で実装完了)

IT5 マルチパースペクティブレビューで指摘された 3 件を統合して起票。
Handling BC の `VerifyClaimAndRegisterCommand` を代表事例として単一 Tx 境界を確立し、
以降の Cross-BC 副作用フロー (US26 Notification、T5-04 状態反映) にも同ポリシーを適用する。

## コンテキスト

IT5 実装で以下 3 件が指摘された。

1. **T5-03 (Tx 境界不統合)**: `VerifyClaimAndRegisterCommand.execute` が
   `verifyAndConsume` (findByBookingId + updateAfterVerify) → `saveHandlingActivity` を
   別トランザクションで実行していた。`saveHandlingActivity` が失敗した場合、
   `updateAfterVerify` の `used_at` / `attempt_count` 更新は残ったまま、Handling レコードは
   作成されず状態不整合が発生する
2. **T5-04 (Cross-BC 状態反映境界不明)**: Handling.Claim → Tracking.TsClaimed の
   状態反映を「どの BC が担うか」「どの Tx に含めるか」が未定義
3. **Notification 副作用 (US26)**: 通知送信 (メール / ログ配信) を Tx 内で行うと DB ロックが
   長期化し、外部システム障害時にロールバックできない副作用が漏れる

また、arch-check Rule 4 (BC 間 Domain 直接参照禁止) の遵守方法として、
Cross-BC helper (Text-based DTO) パターンが IT3-IT5 で確立していたが、
文書化が ADR 単位で存在しなかった。

## 決定

以下 4 項目をトランザクション境界と Cross-BC 参照の統一ポリシーとして採用する。

### 決定 1: Application Command 単位で単一 Tx を確保

副作用を伴う Application Command は、以下のパターンで Tx 境界を宣言する。

- Wire (Servant Handler) 層で `TxRunner.runInTx` を Command 実行全体に適用する
- Repository 実装は Tx を自ら開始しない (T-02 遵守)
- `Cargotracker.Shared.Infrastructure.Db.Transaction.TxRunner` を注入で受ける

```haskell
result <-
  liftIO $ runInTx tx $
    VerifyClaim.execute verifier codeRepo handlingRepo input
```

### 決定 2: Cross-BC 参照は Shared 経由の値型または Application ポート経由に限定

- 他 BC の Domain 型を直接 import してはならない (arch-check Rule 4)
- 共有される型 (例: `Verifier`, `TransportStatus`) は `Cargotracker.Shared.*` に配置
- Cross-BC 呼び出しは Application ports (例: `ConfirmationCodePorts.verifyAndConsumeWith`)
  経由でのみ許可

### 決定 3: 外部副作用 (メール送信・通知配信) は Tx 完了後に実行

- Notification 発火・メール送信・SMS 送信は `runInTx` の外で実行する
- 失敗時は `markFailed` を別 Tx で記録し、`notification_log` テーブルにログを残す
- Tx ロールバック時にも副作用が発火する事故を防ぐ

### 決定 4: Handling.Claim → Tracking.TsClaimed の状態反映は IT6 で実装、Cargo.status 波及は IT8 に繰越

- IT6 (本 ADR): Handling BC が `HandlingActivityRegistered` を発行し、Tracking BC の
  Cross-BC helper (`queryTrackingNumberText` 経由) を呼び出して `Tracking.status` を
  `TsClaimed` に遷移する。ADR-0004 Rule 4 準拠
- IT8: US23 精算処理で `Cargo.status` (Booking BC) への波及を実装する。本 ADR では対象外

## 影響

- **新規ファイル**:
  - `apps/cargo-tracker/src/Cargotracker/Shared/Infrastructure/Db/Transaction.hs` (`TxRunner`)
  - `apps/cargo-tracker/test/unit/Shared/Infrastructure/Db/TransactionSpec.hs` (4 テスト)

- **既存への影響**:
  - `HandlingPageApi.handlingPageApp` シグネチャに `TxRunner` を追加
  - `HandlingPageApi.handlerClaimPost` が `runInTx tx $ VerifyClaim.execute ...` で包む
  - `VerifyClaimAndRegisterCommand.execute` は変更なし (Monad m のまま)
  - `Main.rootApp` の wire に `txRunner = newPostgresTxRunner conn` を追加
  - `Cargotracker.Shared.Security.ConstantTime` に `Verifier` を移設 (Handling ↔ Tracking
    間の Domain 直接参照を回避)

- **将来への影響**:
  - Pricing BC (US21) 実装時、`CalculateShippingCostCommand` も同パターンで Tx 境界を持つ
  - Notification BC (US26) 実装時、Tx 外副作用パターンを適用する

## 代替案

- **代替 A: Repository ポート内で Tx を管理**: ポート実装ごとに Tx を開始すると、
  複数 Repository を跨ぐ Command で Tx 境界が不明確になる。T-02 (Repository は IO のみ)
  違反となるため却下
- **代替 B: `AppM = ReaderT Env IO` で暗黙 Tx 管理**: Env に Connection を持たせて
  暗黙的に withTransaction する案。テストで differentiation 困難、明示的な
  `runInTx` の方が可読性が高いため却下
- **代替 C: すべての Command を Servant ミドルウェアで包む**: Command 単位で
  Tx が必要かは業務要件次第 (単純な read-only query は不要)。細粒度制御のため却下

## 関連

- ADR-0004 (Cross-BC helper Rule 4)
- ADR-0010 (Session Cookie 認証)
- iteration_plan-6.md T5-01/T5-02/T5-03/T5-04 タスク定義
- IT5 マルチパースペクティブレビュー高優先 3 件 (T5-03/T5-04 由来)
