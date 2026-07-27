---
title: イテレーション 6 完了報告書
description: IT6（US14 追跡番号発行・US15 荷役記録・US16 引取記録・US18 追跡照会・Tracking/Handling BC 新設）の完了報告。Phase 2 完了・Release 0.2 到達。
tags: development, iteration-report, iteration-6, go
---

# イテレーション 6 完了報告書

## エグゼクティブサマリー

IT6 は中盤局面（インサイドアウト）の最終イテレーションとして、**Tracking Context（追跡）と Handling Context（荷役）の 2 つの境界付けられたコンテキストを新設**し、追跡番号発行（US14）・荷役作業記録（US15）・引取作業記録（US16）・追跡情報照会（US18）を実装した。実績 14 SP（計画どおり・達成率 100%）。荷役イベントの記録により貨物の輸送状態（TransportStatus）が自動遷移し、荷主・荷受人が追跡番号で貨物の現在状態・位置・イベント履歴を照会できる。これをもって **Phase 2（経路設計・貨物追跡）が完了し、Release 0.2 に到達**した。

BC 独立性は ACL 出力ポート（`CargoSnapshotProvider`・`HandlingEventPublisher`・`TrackingActivityCreator`・`TrackingNumberIssuer`）と合成ルート注入で担保し、`make arch` は全 green。SonarQube Quality Gate は PASS（new_coverage 81.4%）。クローズの XP 5 視点レビューで検出した高優先度の実バグ（CUSTOMS 状態退行・MISROUTED/警告のフィードバック欠落）はクローズ前に修正し、統合テスト（testcontainers）も整備した。

## 達成状況

| ユーザーストーリー | SP | 状態 |
|-------------------|----|----|
| US14 追跡番号を発行する（経路設計者） | 3 | ✅ 完了 |
| US15 荷役作業を記録する（荷役作業員） | 5 | ✅ 完了 |
| US16 引取作業を記録する（荷役作業員） | 3 | ✅ 完了 |
| US18 追跡情報を照会する（荷主・荷受人） | 3 | ✅ 完了 |
| **合計** | **14** | **100%** |

### 成功基準

- [x] US14/US15/US16/US18 の受け入れ基準を満たす（採番・発行通知・荷役記録・状態自動遷移・引取確認・追跡照会・公開照会）。
- [x] Tracking / Handling の集約・値オブジェクト・荷役妥当性検証（`IsValidFor`）・状態遷移の不変条件を domain 層ユニットテストで隔離検証。
- [x] Handling → Booking の貨物参照を ACL ポート（`CargoSnapshotProvider`）で抽象化し `make arch` green。
- [x] ドメイン層カバレッジ 90% 以上（tracking 100%・handling 98.5%）・SonarQube Quality Gate PASS（81.4%）。
- [x] `make check`（build/test/lint/govulncheck/arch）green。（CI は未 push・当ブランチは手動トリガー）
- [~] フルフロー状態遷移のデモ E2E は IT7 繰越（要 app+DB）。到達性・エラー系の E2E スペックは追加済み。

## 技術的成果

### 実装

- **Shared Domain**: 共有列挙 `TransportStatus`（9 段階・注1 命名統一）。
- **Handling Context（新設）**: `HandlingActivity` 集約・`HandlingType`（RECEIVE/LOAD/UNLOAD/CUSTOMS/CLAIM）・`CargoSnapshot`/`LegSnapshot`・`ConsigneeConfirmation`（US16・注3）・`IsValidFor` 荷役妥当性検証デシジョンテーブル。application（`RegisterHandlingActivityService`）・pgx リポジトリ・web（`/handling`・`/handling/new`）。
- **Tracking Context（新設）**: `TrackingActivity` 集約・`TrackingNumber`（TRK-YYYYMMDD-NNNN 採番）・`TrackingActivityEvent`。application（`TrackingCommandService`・`TrackingQueryService` CQRS）・pgx リポジトリ・web（`/tracking`・`/tracking/{n}` htmx 自動更新・`/public/tracking/{n}` 公開）。
- **Booking Context**: `Cargo.IssueTrackingNumber`（CONFIRMED→TRACKING_ISSUED）・`AssignTrackingNumberService`（US14）・cargo の transport_status/tracking_number 永続化・予約詳細の発行導線。
- **DB**: migration 000012-014（cargo 拡張 + tracking 3 表 + handling 2 表）・sqlc per-BC 分離（T7）。
- **配線**: 合成ルートアダプタ 4 種（Handling→Tracking / Booking→Tracking の状態同期）。
- **ADR-0008**: BC 間状態同期の整合性境界（同期 in-process + 明示的既知制約）。

### コード規模

| 指標 | 値 |
|------|-----|
| 変更ファイル | 75 |
| 追加行 | 約 4,422 |
| コミット数 | 20 |

## 品質指標

| 項目 | 結果 |
|------|------|
| `make check`（build/test/lint/govulncheck/arch） | green |
| ドメイン層カバレッジ | tracking 100%・handling 98.5%・shared 97.4%・booking 97.6% |
| application 層カバレッジ | handling 95.2%・tracking 80%・booking 78% |
| リポジトリ統合テスト（testcontainers） | tracking 75%・handling 78%（新規整備） |
| SonarQube Quality Gate | **PASS**（new_coverage 81.4%・重複 0.3%・new_violations 0） |
| BC 独立性（go-arch-lint） | green（ACL/イベント経由のみ・`tracking-sqlcgen`/`handling-sqlcgen` 宣言追加） |

## レビュー結果

developing-review（XP 5 視点並列: programmer/tester/architect/technical-writer/user-representative）を実施。統合レポート: [it6_go_review_20260727.md](../review/it6_go_review_20260727.md)。

**クローズ前に対応した高優先度指摘**:

- CUSTOMS の UNKNOWN による輸送状態退行を修正（CurrentStatus が UNKNOWN を読み飛ばし現状態維持）。
- MISROUTED を追跡照会に EXCEPTION として反映（荷主・荷受人へ検証結果が届く）。
- 荷役登録後の警告/MISROUTED を作業員に flash 表示。
- 公開追跡ページを最新イベントのみ + 反映注記 + 連絡先フッターに是正。
- ErrTrackingNotFound 握り潰しに警告ログ + ADR-0008 で整合性境界を記録。
- tracking/handling の web handler httptest テスト・リポジトリ統合テスト（testcontainers）を整備。

## 課題と残作業

- **BC 間同期の原子性・採番競合**（ADR-0008・IT7）: 発行フローの単一 tx or outbox 化、採番の DB シーケンス化、UNIQUE 衝突リトライ。
- **荷役履歴リプレイ**（ADR-0008・IT7）: 発行前荷役のイベントロスト解消。
- **フルフロー状態遷移 E2E**（IT7）: 発行→RECEIVE→LOAD→照会の一連フロー（要 app+DB）。
- **業務ループ**（IT7）: 協議依頼/通知待ちワークリスト（IT5 由来 T2/T3）、荷役作業員ワークリスト・引取確認の可視化、公開追跡番号の非連番トークン化。
- **CI**: 20 コミット未 push。当ブランチは workflow_dispatch 手動起動のため、push 後にトリガーが必要。

## 次イテレーション（IT7）への引き継ぎ

- **スコープ**: 終盤局面（アウトサイドイン）・Phase 3 精算・例外処理。US17 貨物状態手動更新・US19 遅延例外・US20 破損/紛失例外（release_plan 暫定）。
- **最優先 Try**: BC 間同期の原子化・荷役履歴リプレイ（ADR-0008）、フルフロー E2E とリポジトリ統合テストの開発フェーズ内実施、検証結果フィードバックの DoD 化。
- 詳細は [IT6 ふりかえり](retrospective-6.md) の Try を参照。

## 関連ドキュメント

- [IT6 計画](iteration_plan-6.md)
- [IT6 ふりかえり](retrospective-6.md)
- [IT6 開発レビュー](../review/it6_go_review_20260727.md)
- [ADR-0008 BC 間同期の整合性境界](../adr/0008-bc-sync-consistency-boundary.md)
- [リリース計画](release_plan.md)
