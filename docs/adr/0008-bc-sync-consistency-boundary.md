# ADR 0008: BC 間状態同期（追跡番号発行・荷役イベント）の整合性境界を同期 in-process 配線 + 明示的既知制約とする

IT6（US14 追跡番号発行・US15/US16 荷役記録・US18 追跡照会）で、Booking → Tracking（追跡番号発行に伴う追跡レコード作成）と Handling → Tracking / Booking（荷役登録に伴う輸送状態同期）の BC 間状態同期をどの整合性境界で実現するかを定める。

日付: 2026-07-27

## ステータス

2026-07-27（IT6）承認（暫定）。同期 in-process 配線で実装。原子性・履歴リプレイの強化は後続イテレーションの技術的負債として明示する。

**更新（2026-07-27・IT8）**: 採番原子化（下記 B）を返済。追跡番号採番を `sequence_counter` テーブルの原子操作（`INSERT ... ON CONFLICT (name, day) DO UPDATE SET value = value + 1 RETURNING value`）へ移行し、tx 外 count+1 の競合を排除した。同パターンを Billing の請求番号（invoice_number）にも適用。**荷役履歴リプレイ（下記 C）は Release 1.0 後バックログへ繰越（スコープ外宣言）**。

## コンテキスト

IT6 で追跡・荷役の 2 BC を新設し、以下の BC 横断状態同期が発生する。

1. **追跡番号発行（US14）**: Booking の `AssignTrackingNumberService` が (a) `Cargo` を TRACKING_ISSUED に更新、(b) 追跡番号を採番、(c) Tracking に追跡レコードを作成、(d) 荷主へ通知、を行う。
2. **荷役イベント同期（US15/US16）**: Handling の `RegisterHandlingActivityService` が荷役を保存し、`HandlingActivityRegisteredEvent` を発行 → Tracking が追跡イベントを追記し輸送状態を遷移させる。

BC 独立性（`make arch`）を保ちつつ、部分失敗時の整合性境界を定める必要がある。開発レビュー（IT6・architect / programmer）で以下のストレスポイントが指摘された。

- **A. 発行フローの非原子性**: (a)〜(d) が各々別トランザクションで、(c) 失敗時に「Cargo は発行済みだが Tracking レコード無し」の不整合が残る。
- **B. 採番の競合**: 追跡番号を `日次連番 count+1` で tx 外算出しており、並行発行で同一番号が生成され得る。
- **C. 荷役の追跡未反映**: 追跡番号未発行の貨物に対する荷役は `ErrTrackingNotFound` で追跡タイムラインに反映されない。後で発行されても過去荷役は復元されない（履歴リプレイ経路が無い）。

## 決定

1. **BC 横断配線は同期 in-process の合成ルート注入方式とする**（ADR-0007 の踏襲）。
   - Tracking/Handling/Booking の application に出力ポート（`CargoSnapshotProvider`・`HandlingEventPublisher`・`TrackingActivityCreator`・`TrackingNumberIssuer`）を定義し、実装（変換アダプタ）を `cmd/server` に集約する。BC は互いを直接 import しない。
   - `HandlingEventPublisher` は名称に反し**同期呼び出し**である。将来の非同期化（outbox）に備え、命名の含意を本 ADR で明示する。

2. **本 IT では「単一 BC 内トランザクション + 明示的既知制約」を整合性境界とする**。分散トランザクション・outbox は導入しない。
   - **C（荷役の追跡未反映）への対処**: `ErrTrackingNotFound` は握りつぶすが `slog.WarnContext` でログ出力する。追跡番号未発行時の荷役はまれ（発行 → 受領の順序が正）であり、荷役自体は Handling に永続化される。**追跡レコード作成時に既存荷役をリプレイして履歴を再構築する経路は後続 IT で実装する**（技術的負債として明示）。
   - **妥当性検証フィードバックの反映**: 荷役妥当性検証で MISROUTED が確定した場合、追跡イベントの輸送状態を EXCEPTION として記録し、荷主・荷受人の追跡照会（US18）にも例外として反映する（正常遷移で上書きしない）。CUSTOMS 等の UNKNOWN イベントは現状態を退行させない（`CurrentStatus` が UNKNOWN を読み飛ばす）。

3. **A（原子性）・B（採番競合）は後続 IT の強化対象とする**。
   - 現状は DB の `tracking_activity.tracking_number` UNIQUE 制約により、競合時は**沈黙のデータ破損ではなく例外で顕在化**する（安全側に倒れる）。単一開発者（AI ペア）運用では並行発行は実質発生しない。
   - 後続 IT で (a) 採番を DB シーケンス / 採番テーブルによる原子採番へ、(b) 発行〜追跡レコード作成を単一トランザクション境界 or outbox による結果整合へ、(c) UNIQUE 衝突時のリトライ、を検討する。

## 影響

- IT6 実装: `handlingEventAdapter` が `ErrTrackingNotFound` をログ出力、`RecordHandlingEvent` が Misrouted → EXCEPTION 反映、`CurrentStatus` が UNKNOWN 読み飛ばし（実装・テスト済み）。
- 技術的負債（後続 IT）: 追跡番号採番の原子化、発行フローのトランザクション/outbox 化、追跡レコード作成時の荷役履歴リプレイ。IT7 のふりかえり Try に登録する。
- 統合テスト（testcontainers）による採番一意性・イベント時系列復元の検証は IT7 で追加する。

## 参考

- [ADR-0003](0003-transport-status-canon.md) TransportStatus / RoutingStatus 正典
- [ADR-0005](0005-bc-reference-and-shared-sqlcgen.md) BC 間参照・ACL パターン
- [ADR-0007](0007-route-search-cross-bc-acl.md) 経路探索の BC 横断・合成ルート注入方式
- [IT6 計画](../development/iteration_plan-6.md)
