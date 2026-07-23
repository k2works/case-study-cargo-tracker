# ADR 0006: 追跡状態の純粋関数導出と Booking→Tracking 連携の回復戦略（IT5）

## ステータス

承認（IT5 時点）

## コンテキスト

IT5 で US14-17（追跡番号発行・荷役／引取記録・貨物状態手動更新）を実装するにあたり、Tracking Context の状態管理と、Booking→Tracking / Handling→Tracking の BC 跨ぎ書き込みに関する設計判断が必要になった。

1. **追跡状態の保持方式**: 貨物の輸送状態（`TrackingStatus`）を、集約にフィールドとして持つか、時系列イベントから導出するか。IT5 計画（`iteration_plan-5.md`）で本件を ADR 候補として明示していた。
2. **BC 跨ぎ書き込みの回復性**: [ADR-0004](0004-cross-context-write-consistency.md) は「BC 跨ぎ書き込みは逐次実行し、部分失敗は同一操作の再実行で冪等収束する」ことを保証した。しかし US14 の追跡番号発行（`issue_tracking`）は次の非対称性を持つ。
   - 手順: (1) Booking を `Confirmed → TrackingIssued` へ遷移・保存 → (2) `TrackingActivity` を採番・保存 → (3) 荷主通知
   - (2) が失敗すると「予約は `TrackingIssued`・追跡レコード無し」の中間状態が残る
   - `Cargo::issue_tracking()` は `Confirmed` からのみ遷移を許すため（[ADR-0005](0005-booking-status-state-machine.md)）、`TrackingIssued` から同一操作を再実行しても不正遷移で弾かれ**収束しない**
   - これは ADR-0004 が前提とした route_confirm 型（両操作が同一状態から冪等）とは異なるケースである（[IT5 レビュー](../review/it5_development_review_20260723.md) architect H2）

## 決定

### 1. 追跡状態は保持イベント列からの純粋関数として導出する

`TrackingActivity::current_status()` を、記録済み `TrackingActivityEvent` 列の末尾から導出する純粋関数とする（イベントが無ければ `NotReceived`）。状態を独立フィールドとして二重管理しない。

- 荷役反映（US15/US16）・手動更新（US17）はいずれも `TrackingActivityEvent` を追記するだけで、状態は導出結果として一意に定まる
- IT6 の例外イベント（遅延・破損等）導入時も、導出ロジックの末尾判定を差し替えるだけで拡張できる（変更に対して開いている）

`tracking_activity.transport_status` カラムは、この導出結果を書き込み時に反映する**読み取り最適化キャッシュ**と位置づける（[ADR-0001](0001-cqrs-read-model-placement.md) の Read Model 方針と整合）。書き込み経路が `TrackingActivityRepository::save` のみに統制される限り整合し、追跡一覧・照会クエリで再導出コストを避けられる。将来的に照会が複雑化した場合は CQRS の Read Model 側へ寄せる。

### 2. Booking→Tracking 連携の回復戦略

追跡番号発行の部分失敗に対し、以下を回復戦略として明文化する。

- **再操作の冪等パス**: `issue_tracking` の再実行時、予約が既に `TrackingIssued` かつ追跡レコードが未生成の場合は、Booking 遷移をスキップして追跡レコード生成・通知から再開できる冪等パスを許容する（不正遷移エラーで一律に弾かない）
- **監視による検出**: 「予約 `TrackingIssued` かつ `tracking_activity` に対応レコード無し」を健全性チェック（リコンシリエーション）の検出対象に加える。ADR-0004 の「イベントは即時性のヒント、リコンシリエーションが整合性の保証」の役割分担を踏襲する
- **Handling→Tracking は対称的に冪等**: `reflect_handling`（荷役の追跡反映）は `tracking_number` の upsert ＋イベント追記で、荷役記録保存後の反映失敗も再実行で収束する（ADR-0004 の冪等前提を満たす）

> 本 IT では回復パスの実装までは行わず、上記を規約として記録する。中間状態の実発生時は監視で検出し手動回復する運用とし、実装は不整合インシデントが顕在化した時点（ADR-0004 の Transactional Outbox 移行トリガーと同基準）で行う。

## 影響

- `current_status()` の純粋関数導出により、状態と履歴の二重管理を回避。追跡状態の正しさはイベント列に一元化される
- `transport_status` カラムの位置づけ（Read Model キャッシュ）が明文化され、二重管理の火種が「意図されたキャッシュ」として整理される
- Booking→Tracking の中間状態が「無自覚な負債」ではなく「監視で検出し回復する既知のトレードオフ」として記録され、後続の実装判断（冪等パス実装・Outbox 移行）の基準が明確になる
- BC 独立（domain-tracking / domain-handling が他 domain クレート非依存）は維持され、連携は `interface-web/src/tracking_acl.rs` の ACL アダプター（`ConfirmedBookingIssuer`・`TrackingReflectionPort`）に集約される

## 関連

- [ADR-0001 CQRS Read Model の配置](0001-cqrs-read-model-placement.md)
- [ADR-0004 BC 跨ぎ書き込み一貫性](0004-cross-context-write-consistency.md)
- [ADR-0005 予約状態機械](0005-booking-status-state-machine.md)
- [IT5 開発成果物レビュー](../review/it5_development_review_20260723.md)
- [ドメインモデル設計](../design/domain-model.md)
