---
title: IT8 マルチパースペクティブレビュー統合レポート
description: Billing Context（US21/22/23）・ダッシュボード（US01）・割引ポリシー管理（US-ADM-01）の 5 視点レビューと対応。
tags: review, iteration-8, go, multi-perspective
---

# IT8 マルチパースペクティブレビュー統合レポート

- 対象: IT8 成果物（Billing Context 新設 US21/22/23・ダッシュボード US01・割引ポリシー管理 US-ADM-01・ADR-0008 採番原子化）
- 日付: 2026-07-27
- 手法: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）並列レビュー
- 対象コミット範囲: `4d2c8c9f..HEAD`

## エグゼクティブサマリー

Billing の金額計算（int64 最小通貨単位）・採番原子化・BC 独立性・ドメイン層のテスト網羅は XP の規律に沿った堅実な実装で、godoc 品質も高水準。一方、(1) 支払期限の DATE/TIMESTAMP 境界バグが 00:00 固定テストでマスクされていた点、(2) US23 フルフローの E2E 欠落、(3) 新 BC（Discount Policy Context）が正典 ADR-0002・domain-model.md に未反映という設計ドキュメント乖離、が高優先度として挙がった。**高優先度はすべてクローズ前に対応済み**。中の一部（金額 int32 クランプ・入金確認の部分失敗リカバリ）は設計変更を伴うため Release 1.0 後バックログへ明示的に保留。

## 視点別サマリー

### プログラマー視点
- 高: なし。
- 中1: `ConfirmPayment` の部分失敗（Save で CONFIRMED 確定後 `MarkSettled` 失敗で invoice=CONFIRMED / booking≠SETTLED の不整合、再実行不能）→ **保留**（冪等化/アウトボックスは設計変更・post-1.0）。
- 中2: `toInt32` で金額を int32 に**サイレントクランプ**（21 億円超で誤金額保存）→ **保留**（カラム BIGINT 化 or エラー化。既存 000017 スキーマ変更を伴うため post-1.0）。
- 低: invoice に割引率 CHECK なし・フォームエラーの表示文字列依存・payment テーブル未使用。
- Keep: 金額 int64・採番原子化・BC 独立性・境界日付の再計算回避。

### テスター視点
- 高1: US23 フルフロー（発行→入金確認→SETTLED）の E2E 不在 → **対応**（`billing.spec.ts` にフルフロー追加・seed 000019）。
- 高2: 支払期限 DATE/TIMESTAMP 境界バグ（`MarkOverdue` が当日時刻付きで誤 OVERDUE）→ **対応**（日付単位比較・当日時刻付き境界テスト）。
- 中: `IsActiveOn` 境界未検証 → **対応**（境界テスト追加）。率超過のサーバ側拒否が E2E 未担保 → **対応**（ハンドラテスト追加）。クロス BC 部分失敗・通知失敗分岐・二重請求写像のテスト不足 → 一部対応・残りは保留。
- Keep: Clock/採番/ポートの DI でテスタビリティ高・BVA の基本形。

### アーキテクト視点
- 高: 新 BC 追加が ADR-0002（BC 正典 7）を無記録に破る → **対応**（ADR-0010 起票・ADR-0002 改訂・正典 8 化）。
- 所見: 層分離・BC 独立性・ダッシュボードの DIP 設計は健全（`make arch` green）。割引ポリシーを独立 BC にした判断・直接 pgx 方針を ADR-0010 に記録。

### テクニカルライター視点
- 高（指摘1/2）: domain-model.md が新 BC を未記載・`DiscountPolicy` 二義性未説明、ADR-0002/iteration_plan-8 と実装が矛盾 → **対応**（domain-model.md BC 表更新・ADR-0010・plan 注5 更新）。
- 中（指摘3/4/5）: ui_design.md の請求書 ID 形式旧式 → **対応**。割引ポリシーのフォームエラー汎用潰し → **対応**。請求書/精算書の用語揺れ → **一部対応**（画面表示を「請求書」に統一。ドメインコメントの「精算」表現は業務プロセス語として残置）。
- 低（指摘6/7）: ダッシュボードの ui_design 記述（htmx フラグメント）と実装（サーバレンダリング）差・割引ポリシー画面の salt 図未整備 → **保留**（低・ドキュメント追補）。

### ユーザー代表視点
- ロール別入口の到達性（BILLING/ADMIN）は navbar・ダッシュボード・RequireRole が整合し良好。過去 IT の導線欠落は解消。
- 指摘2（最優先とされた「割引根拠が精算書詳細に出ない」）→ **確認の結果、対象外**。`billing/detail.html` は基本料金・割引率%・割引額・消費税・合計を data-testid 付きで表示済み（誤検出）。
- 指摘3/4（一覧の状態フィルタ・期限ソート、未入金/期限超過の切り分け表示）→ **保留**（UX 改善・post-1.0 バックログ）。

## 対応済み（クローズ前）

| 指摘 | 深刻度 | 対応 |
| :--- | :--- | :--- |
| 支払期限 DATE/TIMESTAMP 境界バグ（MarkOverdue・IsActiveOn） | 高 | 日付単位比較へ正規化・当日時刻付き境界テスト追加 |
| US23 フルフロー E2E 欠落 | 高 | billing.spec.ts にフルフロー追加・CLAIMED seed（000019） |
| 新 BC が ADR-0002・domain-model 未反映 | 高 | ADR-0010 起票・ADR-0002 改訂・domain-model/plan/ui_design 更新 |
| 割引ポリシーのフォームエラー汎用潰し | 中 | ドメインエラーを errors.Is で写像・率超過拒否テスト追加 |
| IsActiveOn 境界未検証 | 中 | 境界テスト追加 |
| 請求書/精算書 用語揺れ（画面） | 中 | 画面表示・エラーメッセージを「請求書」に統一 |
| 請求書 ID 形式のドキュメント乖離 | 中 | ui_design を実装形式（INV-YYYYMMDD-NNNN・{invoiceNumber}）に是正 |

## 保留（Release 1.0 後バックログ・方針明記）

| 指摘 | 深刻度 | 保留理由 |
| :--- | :--- | :--- |
| 金額の int32 サイレントクランプ | 中 | invoice カラム BIGINT 化 or エラー化はスキーマ変更を伴う。post-1.0 で invoice.*_value を BIGINT へ移行し発行時オーバーフローをエラー化。 |
| 入金確認の部分失敗リカバリ（CONFIRMED/未 SETTLED 不整合） | 中 | 冪等化 or アウトボックス導入は整合性設計の変更。ADR-0008 の同期境界と併せ post-1.0 で設計。 |
| 通知失敗分岐・二重請求 HTTP 写像のテスト補強 | 中 | 挙動は実装済み。テスト補強を post-1.0 で追加。 |
| ダッシュボード ui_design 記述差・割引ポリシー salt 図 | 低 | ドキュメント追補（post-1.0）。 |
| 請求一覧の状態フィルタ・期限ソート・未入金/期限超過の切り分け表示 | 低 | UX 改善（post-1.0）。 |

## 結論

高優先度 3 件はクローズ前に対応完了。`make check`（build/vet/test/lint）・`make arch`・integration すべて green。保留項目は深刻度・理由・対応時期を明記し Release 1.0 後バックログへ引き継ぐ。IT8 はクローズ可能。
