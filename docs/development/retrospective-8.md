---
title: イテレーション 8 ふりかえり
description: IT8（US21 料金算出・US22 法人割引・US23 精算・US01 ダッシュボード・US-ADM-01 割引ポリシー管理）の KPT ふりかえり。Billing Context を新設し Release 1.0 に到達。
tags: development, retrospective, iteration-8, kpt, go
---

# イテレーション 8 ふりかえり（KPT）

対象: IT8（2026-07-27 開発完了・クローズ）。**最終イテレーション**として Billing Context（精算）を新設し、US21/US22/US23 で **全 25 US を実装し Release 1.0 に到達**。加えてウォーキングスケルトンでプレースホルダ表示だった 2 画面（US01 ダッシュボード・US-ADM-01 割引ポリシー管理）をクローズ時に実装し、独立 BC「Discount Policy Context」を新設（BC 正典 7→8・ADR-0010）。実績 13 SP（精算）＋クローズ時追加 2 画面。

## Keep（うまくいったこと）

### 技術的成功

- **金額を int64（最小通貨単位）で表現し丸め誤差を排除**: `Money`（Add/Subtract/MultiplyRate・away-from-zero 丸め）を土台に、基本料金→法人割引→消費税→合計の計算を Invoice 集約に凝集。5 視点レビューで programmer/tester が「金銭ドメインの規律が効いている」と高評価。
- **請求番号採番を原子化（ADR-0008 返済）**: 追跡番号採番の原子化（`INSERT ... ON CONFLICT DO UPDATE RETURNING` 単一文）を **IT8 Day 1 で先に返済**し、invoice_number も同一パターンで統一。2 IT 連続繰越だった負債を序盤独立コミット枠で解消（前 IT ふりかえり Try の実践）。
- **BC 独立性を維持したまま精算フローを完成**: 法人割引率は Shipper への ACL（`ShipperContractProvider`）、予約 SETTLED 遷移は `BookingSettler` ACL、貨物スナップショットは合成ルート注入で取得。`make arch` 全 green・他 BC 直接 import なし。
- **プレースホルダ 2 画面を TDD で実装しウォーキングスケルトンを解消**: ダッシュボードは各 BC クエリを合成ルートで束ねる `SummaryProvider`（DIP）で BC 独立性を保ちつつ横断集約。割引ポリシー管理は独立 BC として domain 92.7% のテストで新設。

### プロセス的成功

- **クローズ時のマルチパースペクティブレビューで潜在バグを摘出・是正**: 5 視点並列レビューで (1) 支払期限 DATE/TIMESTAMP 境界バグ（00:00 固定テストでマスクされていた）、(2) US23 フルフロー E2E 欠落、(3) 新 BC の正典未反映、を検出し**クローズ前に全対応**。特に境界バグはメモリに記録済みの再発パターンで、レビューが安全網として機能した。
- **SonarQube Quality Gate を PASS で確定**: new_coverage 81.1%・violations 0（テンプレートパス重複を定数化で解消）・重複 0.49%・Bug 0・Vulnerability 0。

## Problem（うまくいかなかったこと・課題）

- **新 BC 追加が正典 ADR・計画と矛盾したまま実装された**: 割引ポリシー管理を独立 BC にした際、ADR-0002（BC 数ドリフト防止）・domain-model.md・iteration_plan-8 注5（「テーブルを作らない・スコープ外」）を更新せずに実装。architect/technical-writer に「正典が実装に追いつかず、まさに ADR-0002 が防ごうとしたドリフトの再発」と指摘され、クローズ時に ADR-0010 起票・正典 8 化で是正した。**実装と設計の同時反映が崩れた**。
- **境界バグがテストの盲点で見逃されていた**: `MarkOverdue`・`IsActiveOn` の期限判定を timestamp で行い、テストが全て 00:00 だったため「期限当日に時刻付きで払うと超過扱い」の偽陰性が隠れていた。メモリの既知パターンを実装時に適用できていなかった。
- **金銭ドメインに未解決の設計課題が残る**: 金額の int32 サイレントクランプ（21 億円超で誤保存）・入金確認の部分失敗リカバリ不能（invoice CONFIRMED / booking 未 SETTLED）は、スキーマ変更・整合性設計を伴うため Release 1.0 後バックログへ保留とした。
- **CI がブランチ手動トリガー（workflow_dispatch）で IT8 コードに対して未実行**: ローカル同等ゲート（make check・arch・integration）＋ SonarQube PASS で品質を担保したが、リモート CI での検証は push 保留。

## Try（次サイクルへの改善アクション）

Release 1.0 後（保守・拡張フェーズ）のバックログとして引き継ぐ。

- **T1（高・金銭整合性）金額カラムを BIGINT へ移行しオーバーフローをエラー化**: `invoice.*_value` を INTEGER→BIGINT に migration し、`toInt32` のサイレントクランプを撤廃。発行時に上限超過を検知したらエラーで中止。担当: 次サイクル序盤の独立コミット枠。期待効果: 誤金額保存の根絶。
- **T2（高・整合性設計）入金確認のクロス BC 部分失敗を冪等化 or アウトボックス化**: `ConfirmPayment` を「MarkSettled 先行 or CONFIRMED 状態でも再送可能」に設計変更し、ADR-0008 の同期境界と併せて ADR 化。期待効果: invoice CONFIRMED/booking 未 SETTLED の不整合の回復可能化。
- **T3（プロセス）新 BC/新テーブル追加時は ADR・domain-model・plan を実装と同一 PR で更新**: 「スコープ変更（外→内）が起きたら正典 3 点（ADR-0002/domain-model/該当 plan）を同時更新」をチェックリスト化。期待効果: 正典ドリフトの再発防止。
- **T4（プロセス）期限・有効期間など日付比較の実装時に「当日時刻付き」テストを必須化**: メモリ [[feedback_date-vs-timestamp-deadline]] を実装直後チェックに組み込む。期待効果: 境界偽陰性の再発防止。
- **T5（UX・低）請求一覧の状態フィルタ・期限ソート・未入金/期限超過の切り分け表示**、**ダッシュボード ui_design 追随・割引ポリシー salt 図追補**。期待効果: 経理・管理業務の運用性向上とドキュメント整合。

## 次サイクルへの引き継ぎ（持ち越し事項）

- Release 1.0 到達により、以降は**保守・拡張フェーズ**。上記 Try T1〜T5 と、既存バックログ（ADR-0009 のエスカレーション再評価・`TrackingExceptionDetectedEvent` 配信・管理職ワークリスト・T3b 荷役履歴リプレイ・US21 例外時料金調整）を保守バックログとして一元管理する。
- Discount Policy Context の割引ポリシーを Billing の請求フローへ適用する連携は未実装（ADR-0010 で将来対応と明記）。現状の請求時割引率は引き続き Shipper ACL から取得。
