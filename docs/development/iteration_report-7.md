---
title: イテレーション 7 完了報告書
description: IT7（US17 貨物状態手動更新・US19 遅延例外・US20 破損/紛失例外）の完了報告。Tracking Context の例外処理を実装。
tags: development, iteration-report, iteration-7, go
---

# イテレーション 7 完了報告書

## エグゼクティブサマリー

IT7 は終盤局面（アウトサイドイン）の初回として、**Tracking Context の例外処理**（遅延・破損・紛失）と**貨物状態手動更新**を実装した。実績 13 SP（計画どおり・達成率 100%）。IT6 で「枠のみ」だった `tracking_exception_event` を、例外種別・エスカレーション判定・対応報告・解決の業務フローとして作り込んだ。例外発生で貨物状態が EXCEPTION に遷移し、荷主に通知され、紛失（LOST）は緊急フラグが立ち管理職へエスカレーションされる。例外解決で発生前の状態に自然復帰する。

ドメイン層カバレッジ 93%+、`make arch` green、SonarQube Quality Gate PASS（80.3%）。クローズの XP 5 視点レビューで検出した高優先度（通知の非トランザクション契約・荷主への対応報告未可視化・荷役作業員の破損登録導線欠落・サービス層エスカレーション未検証）はクローズ前に是正。エスカレーション再評価・イベント配信・管理職ワークリスト等は ADR-0009 / IT8 の課題として明示繰越した。

## 達成状況

| ユーザーストーリー | SP | 状態 |
|-------------------|----|----|
| US17 貨物状態を手動更新する（追跡管理者） | 3 | ✅ 完了 |
| US19 遅延例外を処理する（追跡管理者） | 5 | ✅ 完了 |
| US20 破損・紛失例外を処理する（追跡管理者・荷役作業員） | 5 | ✅ 完了 |
| **合計** | **13** | **100%** |

### 成功基準

- [x] US17/US19/US20 の受け入れ基準を満たす（状態手動更新・例外登録・EXCEPTION 遷移・通知・エスカレーション・対応報告・履歴）。
- [x] `TrackingExceptionEvent`・`ExceptionType`・`EscalationPolicy`（LOST 即時 / DELAY 48h 超過）を domain 層で隔離検証（48 時間境界をテーブル駆動）。
- [x] 例外解決で TransportStatus が発生前状態に復帰することを検証。
- [x] ドメイン層カバレッジ 90% 以上（93%+）・SonarQube Quality Gate PASS（80.3%）。
- [x] `make check` green・`make arch` green。（CI は未 push・当ブランチは手動トリガー）
- [x] フルフロー統合テスト（例外ライフサイクル）を開発フェーズ内で実施（T5）。フルフロー E2E の seed 整備は IT8 繰越。

## 技術的成果

### 実装

- **例外ドメイン**: `ExceptionType`（DELAY/DAMAGE/LOST/CUSTOMS_HOLD）・`TrackingExceptionEvent`（集約内エンティティ）・`EscalationPolicy`（ステートレスドメインサービス・LOST 即時/DELAY 48h）・`TrackingActivity.AddException`/`ResolveException`/`HasActiveException`。`CurrentStatus` が未解決例外で EXCEPTION、解決で発生前状態に復帰。二重解決を `ErrExceptionAlreadyResolved` で拒否。
- **application**: `ExceptionService`（RegisterException・ResolveException・ManualUpdateStatus）。荷主・管理職通知はベストエフォート（NotificationPort）。手動更新は EXCEPTION 直接指定を拒否。
- **永続化**: migration 000015（resolution_notes/location_unlocode）・例外 sqlc クエリ・リポジトリ例外永続化（id ベース INSERT/UPDATE）。
- **interfaces**: `ExceptionHandler`（`/tracking/{n}/exceptions`・resolve・status-update）+ テンプレート。追跡詳細に発生状況・対応報告を表示（荷主に届く）。ROLE_TRACKER + 破損は ROLE_HANDLER。
- **ADR-0009**: 追跡例外設計（集約内管理・通知ベストエフォート・エスカレーション登録時 1 回評価・IT8 課題明示）。

### コード規模

| 指標 | 値 |
|------|-----|
| 変更ファイル | 38 |
| 追加行 | 約 1,657 |
| コミット数 | 9 |

## 品質指標

| 項目 | 結果 |
|------|------|
| `make check`（build/test/lint/govulncheck/arch） | green |
| ドメイン層カバレッジ | tracking 93%+（例外含む） |
| application 層カバレッジ | tracking 80%+ |
| リポジトリ統合テスト（testcontainers） | 例外ライフサイクル（登録→EXCEPTION→解決→復帰）green |
| SonarQube Quality Gate | **PASS**（new_coverage 80.3%・重複 0.3%・new_violations 0） |
| BC 独立性（go-arch-lint） | green（NotificationPort は tracking/application・他 BC 直接依存なし） |

## レビュー結果

developing-review（XP 5 視点並列）を実施。統合レポート: [it7_go_review_20260727.md](../review/it7_go_review_20260727.md)。

**クローズ前に対応した高優先度指摘**:

- 通知をベストエフォート化（Save 後の通知失敗でユースケースを失敗させない）。
- 追跡詳細に発生状況・対応報告を表示（荷主への US19 業務ループを閉じる）。
- 破損/紛失登録を ROLE_HANDLER にも開放（US20 荷役作業員の入口）。
- DELAY 48h サービス層エスカレーション・DAMAGE・二重解決・複数例外部分解決のテスト追加。
- 手動更新の EXCEPTION 拒否・CUSTOMS_HOLD 手動除外・二重解決拒否。
- 設計是正（test_strategy 旧 API・トレーサビリティ US 番号・domain-model 注3）。

## 課題と残作業

- **ADR-0009（IT8）**: エスカレーション登録後再評価・`TrackingExceptionDetectedEvent` 配信・管理職向け緊急例外ワークリスト・ETA 構造化・紛失解決の CLOSED 終端・index→安定 ID。
- **ADR-0008（IT8・2 IT 連続繰越）**: 追跡番号採番原子化（T3）・荷役履歴リプレイ（T4）。IT8 序盤で先に着手し負債固定化を解消する（ふりかえり Try T3）。
- **フルフロー E2E の seed 整備**（IT8）: skip 依存の撤廃。
- **CI**: 未 push。当ブランチは workflow_dispatch 手動起動のため push 後にトリガーが必要。

## 次イテレーション（IT8）への引き継ぎ

- **スコープ**: 終盤局面・Phase 3 精算。US21 輸送料金算出・US22 法人割引・US23 精算（Billing Context・13SP）→ **Release 1.0（全機能）到達**。
- **最優先 Try**: ADR-0008/0009 の高優先度負債を IT8 序盤で返済（採番原子化・履歴リプレイ・エスカレーション再評価・イベント配信）。フィードバック到達の実装時チェックリスト化。
- 詳細は [IT7 ふりかえり](retrospective-7.md) の Try を参照。

## 関連ドキュメント

- [IT7 計画](iteration_plan-7.md)
- [IT7 ふりかえり](retrospective-7.md)
- [IT7 開発レビュー](../review/it7_go_review_20260727.md)
- [ADR-0009 追跡例外設計](../adr/0009-tracking-exception-design.md)
- [リリース計画](release_plan.md)
