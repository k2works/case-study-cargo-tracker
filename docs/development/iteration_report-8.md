---
title: イテレーション 8 完了報告書
description: IT8（予備・US23-5 未払い通知・US21-6 料金調整・T37 新到着予定日・受入基準完全充足・Release 1.1）の成果・品質指標・課題
date: 2026-07-29T00:00:00.000Z
---

# イテレーション 8 完了報告書

## エグゼクティブサマリー

予備（バッファ）イテレーションとして、IT7 でクローズ時に正直に未達と記録した受入基準の残（US23 受入基準5「支払期限超過の未払い通知」・US21 受入基準6「例外時の料金調整」）と、IT6 の Try（T37 新到着予定日の構造化）を消化しました。あわせて業務全体を実行できるシードデータを整備しました。計画 9 SP を 100% 消化し、RSpec 416 examples 0 failures・全体カバレッジ 95.94%・SonarQube Quality Gate PASS・CI success を達成。マルチパースペクティブレビュー（5 視点）の高優先 4 件をクローズ前に全対応し、特に「OVERDUE 請求書の塩漬け」という実業務を止める欠陥を是正しました。本イテレーションで **MVP の全受入基準（US01-US27）が完全充足**し、**Release 1.1** を発行します。

## 達成状況

| 項目 | 内容 | 状態 |
|:---|:-----|:-----|
| US23-5 | 支払期限超過時の未払い通知（OVERDUE 駆動バッチ・経理通知） | 完了 |
| US21-6 | 例外時の料金調整（減額・補償費用の明細・total 再計算） | 完了 |
| T37 | 遅延の新到着予定日を構造化し公開追跡に反映 | 完了 |
| **計** | | **9 SP・100%** |

## 技術的成果

- **US23-5 未払い通知**: `MarkOverdueInvoices`（期限超過の PENDING を検出→OVERDUE 遷移→`invoice_overdue` 発行・実遷移時のみ通知・冪等）・`pending_overdue` クエリ・`billing:mark_overdue` Rake タスク（cron 駆動・運用手順ドキュメント整備）・経理宛未払い通知。
- **US21-6 料金調整**: `InvoiceLineItem`（減額 REDUCTION/補償 COMPENSATION・符号をドメインで正規化）・`Invoice#add_adjustment`（total 再計算・0 未満の下限ガード・未精算 PENDING/OVERDUE のみ）・`AdjustFreight`・請求書詳細に調整明細/入力フォーム。
- **T37 新到着予定日**: `tracking_exception_events.revised_arrival_date`・対応報告で構造化入力・公開追跡の推定到着日に最優先反映。
- **OVERDUE 塩漬けの是正**: `PaymentStatus#unsettled?`（PENDING/OVERDUE）を許可条件にし、期限超過後の遅延入金・減額を可能に。
- **業務全体シード**: 航海・見積・フルライフサイクル（予約→追跡→荷役→請求→精算）・遅延例外・未払い請求を development 限定・冪等に投入。

## 品質指標

| 指標 | 結果 | 目標 | 判定 |
|:--|:--|:--|:--|
| RSpec | 416 examples 0 failures | 全 green | ✅ |
| 全体カバレッジ（Line） | 95.94% | 80% 以上 | ✅ |
| 新規コードカバレッジ | 91.5% | 80% 以上 | ✅ |
| SonarQube Quality Gate | PASS | PASS | ✅ |
| 重複率 | 0.0% | 3% 未満 | ✅ |
| CI（Backend CI） | success | success | ✅ |
| RuboCop / Brakeman / bundler-audit / Packwerk | 0 / 0 / 0 / 0 | 0 | ✅ |

## レビュー結果

マルチパースペクティブレビュー（5 視点）を実施。高優先度 4 件をすべてクローズ前に対応:

- **H1（programmer + user-rep 収束）**: OVERDUE 請求書の塩漬け → `unsettled?` で入金確認/調整を許可。
- **H2（programmer + architect 収束）**: 符号正規化のアプリ層漏れ → `InvoiceLineItem` のドメインに閉じる。
- **H3（tester）**: 境界値・冪等の通知件数未検証 → テスト追加。
- **H4（tech-writer）**: 未払いバッチの運用ドキュメント欠落 → `docs/operation/未払い通知バッチ運用手順.md` 追加。

中・低の一部（補償費用の増減方向・調整取消/監査・シードの privacy バイパス）は IT9 へ引き継ぎ。詳細は [IT8 実装レビュー](../review/IT8実装_review_20260729.md)。

## 課題と残作業（IT9 引き継ぎ）

- **業務確認（T45）**: 「補償費用」の増減方向を業務ユーザーと確定（現状は追加請求＝正値）。
- **Try**: T43（現場ケース起点の状態機械設計）・T44（ドメイン不変条件の閉じ込め）・T46（シードの privacy バイパス解消）・T47（料金調整の取消/監査/導線）。
- **将来**: 未払いバッチの Solid Queue ジョブ化・OVERDUE 後の再通知/エスカレーション・通知の Outbox 化。

## 次イテレーション（IT9）引き継ぎ

- MVP の全受入基準（US01-US27）を完全充足。Release 1.1 発行。
- IT9 以降は品質改善・運用自動化・業務精緻化の位置づけ。

## 関連ドキュメント

- [イテレーション 8 計画](iteration_plan-8.md)
- [イテレーション 8 ふりかえり](retrospective-8.md)
- [IT8 実装レビュー](../review/IT8実装_review_20260729.md)
- [未払い通知バッチ運用手順](../operation/未払い通知バッチ運用手順.md)
- [ドメインモデル](../design/domain-model.md)（invoice_overdue・InvoiceLineItem）
- [データモデル](../design/data-model.md)（adjustment_type・revised_arrival_date）
- [リリース計画](release_plan.md)

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:-----|:---|:---------|:-----|
| 2026-07-29 | 初版 | IT8 完了報告書を作成 | 開発チーム |
