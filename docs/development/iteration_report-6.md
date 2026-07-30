---
title: イテレーション 6 完了報告書
description: IT6（追跡照会・遅延/破損/紛失例外・通関集約化）の完了報告。Phase 3 完了（Release 0.8）
date: 2026-07-30
---

# イテレーション 6 完了報告書

## エグゼクティブサマリー

IT6 は US18（追跡照会）・US19（遅延例外）・US20（破損・紛失例外）の 11SP を達成率 100% で完了し、**Phase 3（荷役・追跡・例外処理）を完了、Release 0.8 に到達した**。認証不要の公開追跡ページ、htmx 30 秒ポーリング、例外の記録 → 通知 → エスカレーション → 対応報告 → 解決（発生前状態への復帰）、通関申告の独立集約化（ADR-010）と CUSTOMS_HOLD 自動登録までを終盤アウトサイドイン（受け入れテスト先行）で実装した。本 IT から計画・受入・検証をメイン、実装を Opus エージェント（5 委譲）とする分業体制を初適用し、全バッチを verify green で受け入れた。IT5 ふりかえりの Try 6 件をすべて返済した。

## 概要

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-10-05 〜 2026-10-18（計画 Week 11-12） / 2026-07-30（実績記録） |
| 目標 SP / 実績 SP | 11 / 11（達成率 100%） |
| 対象ストーリー | US18・US19・US20 |
| コミット数 | 11（実装 6 + ADR/レビュー/同期 5） |
| 累計 SP | 73 / 81 |
| Phase 3 進捗 | 21 / 21 SP（**完了・Release 0.8**） |
| 体制 | 実装は Opus エージェントへ委譲（グループ単位 5 委譲 + レビュー修正 1 委譲） |

## 達成状況

| ID | ストーリー | SP | 状態 | 備考 |
| :--- | :--- | :--: | :--- | :--- |
| US18 | 追跡情報を照会する | 5 | 完了 | 港湾名・推定到着日・履歴時系列・htmx 30 秒ポーリング（CLAIMED で停止）・認証不要の公開ページ（情報露出最小化・例外要約付き） |
| US19 | 遅延例外を処理する | 3 | 完了 | 記録 → EXCEPTION → 荷主通知 → 対応報告（新到着予定日・対応方針）→ 解決で発生前状態へ復帰。対応履歴を例外行に永続化 |
| US20 | 破損・紛失例外を処理する | 3 | 完了（一部次 IT） | LOST は escalationFlag + ESCALATION 通知記録。荷役作業員は破損・紛失のみ登録可（二重防御）。管理職の実効的な確認導線は IT7 引き継ぎ |

## 技術的成果

- **Domain**: `TrackingExceptionEvent`（statusBeforeException の永続化・報告/解決履歴）・`ExceptionType`・EXCEPTION 優先の `currentStatus()`（解決後は発生前状態とイベント由来状態の進んでいる方へ復帰・タイブレーク決定化）、`CustomsDeclaration` 独立集約（遷移規則・clearedAt 不変・ADR-010）、`transportPhaseRank`（輸送フェーズ順序）。
- **Application / イベント**: `RegisterExceptionService`（種別×ロール認可・冪等）・`customs.held` → CUSTOMS_HOLD 自動登録の冪等リスナー・イベント契約の `shared/contracts` 集約（Try T5）・追跡レコード遅延作成の upsert 冪等化（Try T6）・荷主連絡先解決のポート統一（Try T4）。
- **Infrastructure**: migration 008（consignee_confirmation）・009（tracking_exception_event）。
- **Presentation / UI**: 公開貨物追跡 `/public/tracking/{tn}`（最小表示 + ETA + 例外要約）、StatusTimeline フラグメント（EXCEPTION 赤バッジ・ポーリング停止）、例外登録/一覧（対応状況バッジ・確認ステップ付き解決）、通関ステータス画面（許可遷移のみ表示・HELD 対応手順）、荷役一覧からの例外・通関導線、ナビ×コントローラのロール整合自動検証（Try T2・48 ケース）。
- **設計同期**: ADR-010 起票、domain-model（例外・通関集約化・customs.held）・data-model（migration 008/009・誤記是正）・ui_design（MISSING→LOST・URL 統一・実装準拠化）を同期。

## 品質指標

| メトリクス | 実績 | 目標 | 判定 |
| :--- | :--- | :--- | :--- |
| `npm run verify` | 61 files / 489 tests green | 全 green | PASS |
| lint / typecheck / dependency-cruiser | no violation | 全 green | PASS |
| カバレッジ（全体 statements） | 94.1% | 75% | PASS |
| E2E（Playwright） | 8 passed | success | PASS |
| CI（Lint/Typecheck/Arch/Test・E2E） | success（run 30519225809） | success | PASS |
| SonarQube Quality Gate | **PASS**（新規カバレッジ 92.1%・重複 0.57%・新規違反 0） | PASS | PASS |

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを実施（[レビューレポート](../review/IT6実装_review_20260730.md)）。

主なクローズ内対応（11 件）: 例外解決の復帰先が例外中に到着したイベントを失う欠陥の是正（High）、公開ページへの推定到着日・例外要約の追加（High・US18/US19 の業務価値）、解決済み例外への報告拒否、解決操作の確認ステップ、EXCEPTION 赤バッジ、通関画面の許可遷移のみ表示、DAMAGE 非エスカレーション負テストほか。

次 IT 引き継ぎ（10 件）: 認証境界の fail-closed 化（ADR 起票）、notification_record の所有 + 通知本文設計 + 管理職エスカレーションの実効化、CUSTOMS_HOLD 冪等キーの業務確認、未解決例外の横断一覧、AFTER_COMMIT 構造化・ACL 直読返済ロードマップ等。詳細は [ふりかえり](retrospective-6.md) の Try に反映済み。

## 課題と残作業

- 通知は記録ベースのスタブのままで、本文（新到着予定日・対応方針）が荷主に届く経路は公開ページ要約による部分対応。実配信・本文設計は IT7 の ADR 判断（Try T3）。
- エスカレーションの宛先は固定スタブで、管理職ロール・横断ビューは未実装（IT7 判断）。
- ONBOARD_CARRIER / AWAITING_CLAIM の自動導出（スケジュール連携）・UNKNOWN 遷移は引き続きスコープ外。

## 次イテレーション（IT7）への引き継ぎ

- スコープ: US21/US22/US23（8SP・Phase 4・Release 1.0）。Billing Context 着手（`CargoClaimedEvent` 購読が精算開始点）。局面は終盤（アウトサイドイン）継続。
- ふりかえり Try 6 件（状態合成列の追加、fail-closed 化、通知 ADR、Sonar ゲート手順、CUSTOMS_HOLD 業務確認、Opus 分業標準化）を IT7 計画へ反映する。
- 最終 IT のため Release 1.0 リリース条件（カバレッジ・セキュリティチェックリスト）を計画 DoD へ展開する。

## 関連ドキュメント

- [イテレーション 6 計画](iteration_plan-6.md) / [ふりかえり](retrospective-6.md)
- [IT6 実装レビュー](../review/IT6実装_review_20260730.md)
- [ADR-010 通関申告の独立集約化](../adr/010-customs-declaration-aggregate.md)
- [リリース計画](release_plan.md)
