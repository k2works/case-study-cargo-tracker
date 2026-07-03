# 0014 例外処理の状態遷移ポリシー (Exception → Tracking)

Exception BC の例外記録 (`exception_record`) と Tracking BC の輸送ステータス
(`tracking_activity.transport_status`) の連携方針、Tx 境界、Cargo.status
(Booking BC) への波及タイミングを確定する

日付: 2026-07-03

## ステータス

提案 (2026-07-03、IT7 Ralph Loop iteration 19 で起票)

`Cargotracker.Exception.Domain.Model.ExceptionRecord` / Application 層の
`RecordDelayExceptionCommand` / `RecordDamageExceptionCommand` /
`RecordLossExceptionCommand` (Ralph Loop iteration 13-14 で実装) の
Cross-BC 連携ポリシーを本 ADR で確定させる。

## コンテキスト

US19 (遅延) / US20 (破損・紛失) の Domain / Application 層は Ralph Loop
iteration 10-15 で完成した。次段として以下 3 点の設計判断が必要:

1. **Tracking 状態遷移との Tx 境界**: exception_record INSERT と
   tracking_activity.transport_status UPDATE を単一 Tx にまとめるか
2. **遷移可否のルール**: どの `TransportStatus` から `TsDelayed` /
   `TsDamaged` / `TsLost` (仮) への遷移を許可するか
3. **Cargo.status (Booking BC) 波及**: 例外発生時に Booking の
   キャンセルや精算保留を波及させるかどうか

ADR-0012 (Tx 境界と Cross-BC 参照ポリシー) の 4 原則:

- Single Tx: 複数集約の書き込みは Application で withDbTransaction を張る
- Text-DTO: BC 間参照は Text (bookingId / trackingNumber)
- 副作用外出し: 通知・メールは Tx 完了後
- Cross-BC helper: 被参照 BC の Ports に helper を置く

の枠組みで本 ADR の判断を行う。

## 決定

### 1. Tx 境界: exception_record と Tracking 状態遷移は単一 Tx

`Record*ExceptionCommand.execute` 内で `withDbTransaction` を張り、以下 2 件を
不可分に実行する:

```
BEGIN;
  INSERT INTO exception_record (...);
  UPDATE tracking_activity
    SET transport_status = ?, updated_at = NOW()
    WHERE tracking_number = ?;
COMMIT;
```

理由: 例外が記録されたのに Tracking 状態が変わらない (逆も然り) の不整合を
DB レベルで排除する。ADR-0012 の Single Tx 原則を適用。

Cross-BC helper は Tracking BC 側の `Tracking.Application.Ports` に
`markDelayedByTn / markDamagedByTn / markLostByTn` (Text-DTO 受入) を追加。
Exception BC はこれらを呼び出すのみで、Tracking Domain 型に依存しない
(Rule 4 準拠)。

### 2. 遷移ルール: TsDelivered 以外は原則遷移可

`TransportStatus` (現行 9 値) からの遷移可否は以下:

| From | Delay 可 | Damage 可 | Loss 可 |
| :--- | :--- | :--- | :--- |
| TsNotReceived | ❌ (未受領時に遅延は成立しない) | ❌ | ❌ |
| TsReceived | ✅ | ✅ | ✅ |
| TsLoaded | ✅ | ✅ | ✅ |
| TsOnboardCarrier | ✅ | ✅ | ✅ |
| TsUnloaded | ✅ | ✅ | ✅ |
| TsAwaitingClaim | ✅ | ✅ | ✅ |
| TsClaimed | ❌ (引取完了後は例外扱わず) | ❌ | ❌ |
| TsInException | ❌ (二重例外は追記型で管理) | ❌ | ❌ |
| TsUnknown | ✅ | ✅ | ✅ |

- `TsDelivered` は現行 `TransportStatus` に含まれないが、将来追加時は全遷移
  禁止 (配達完了後の例外は業務外)
- `TsClaimed` / `TsInException` からの遷移禁止は Application 層で検証し、
  違反時は `InvalidTrackingTransition` エラーで拒否

`TsDelayed` / `TsDamaged` / `TsLost` は本 ADR 提案時点では `TsInException`
に統合する (現行 `TransportStatus` の値を維持し、詳細は exception_record
の型・severity で区別)。将来 UI 要件が明確化したら別 ADR で細分化を検討。

### 3. Cargo.status (Booking BC) 波及: US23 精算処理 (IT8) で対応

例外発生時の Booking BC 状態波及 (自動キャンセル、精算保留、返金) は本 ADR
のスコープ外とする。理由:

- US23 (精算処理) の仕様が確定していない (IT8 対応)
- 波及ルールは Billing の観点が必要で Exception BC 単独では決められない
- ADR-0012 の Cargo.status 波及ポリシー (US23 で扱う) と整合

IT7 段階では Exception BC は Tracking のみを更新する。

## 結果

- **良**:
  - exception_record と tracking_activity の整合性を DB レベルで保証
  - Cross-BC helper 経由で Rule 4 (BC 間 Domain 直接参照禁止) を維持
  - 遷移ルールを 9×3 マトリクスで明示、Application 層で検証可能
  - US23 (IT8) に依存する複雑な波及ロジックを本 IT に持ち込まない
- **悪**:
  - `TsInException` に 3 種を統合する暫定策のため、UI での種別表示は
    exception_record を JOIN する必要がある
  - Cross-BC helper 3 種 (markDelayed/Damaged/Lost) を Tracking BC Ports に
    追加する変更が発生する
- **補**:
  - Cross-BC helper は既存の `markClaimedByBookingId` (Handling → Tracking)
    と同じパターンで実装可能
  - `TsDelayed` / `TsDamaged` / `TsLost` の細分化は別 ADR で扱う (次期候補)

## 実装計画 (次イテレーション以降)

### Phase 1: Cross-BC helper

`Cargotracker.Tracking.Application.Ports` に追加:

```haskell
-- | ADR-0014: Exception → Tracking 状態遷移 helper
markInExceptionByTn ::
  Monad m => TrackingRepository m -> Text -> m (Either DomainError ())
markInExceptionByTn repo tn = do
  mActivity <- findByTrackingNumber repo (unsafeTrackingNumber tn)
  case mActivity of
    Nothing -> pure (Left (TrackingNotFound tn))
    Just a -> case checkTransitionForException (taTransportStatus a) of
      Left err -> pure (Left err)
      Right () ->
        updateTransportStatus repo (taBookingId a) TsInException
```

### Phase 2: Application 層で Tx 境界統合

`RecordDelayExceptionCommand.execute` を TxRunner 注入形式に変更:

```haskell
execute repo trackingPort txRunner input = do
  runInTx txRunner $ \tx -> do
    saveException (repo tx) record
    markInExceptionByTn (trackingPort tx) trackingNumber
```

### Phase 3: 遷移検証テスト

- Domain 純粋関数 `checkTransitionForException :: TransportStatus -> Either DomainError ()`
  で 9 状態 × 遷移可否を hedgehog property で網羅
- Application 層で TsClaimed 状態から Record*Exception 失敗を hspec で検証

## 影響範囲

- Cargotracker.Tracking.Application.Ports: `markInExceptionByTn` 追加
- Cargotracker.Exception.Application.Record{Delay,Damage,Loss}ExceptionCommand:
  TxRunner + Tracking Port 注入形式に変更
- domain-model.md §Tracking: 遷移マトリクスを追記
- iteration_plan-7.md タスク 3.3 / 4.2: 本 ADR 反映

## 参照

- ADR-0012 (トランザクション境界と Cross-BC 参照ポリシー)
- ADR-0004 (Cross-BC 参照に ShipperRef VO 導入)
- Cargotracker.Exception.Domain.Model.ExceptionRecord (Ralph Loop iter 12)
- Cargotracker.Exception.Application.Record*ExceptionCommand (iter 13-14)
- iteration_plan-7.md §3 (US19) / §4 (US20) / §Transaction Boundary
