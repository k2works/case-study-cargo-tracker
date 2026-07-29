---
title: イテレーション 3 ふりかえり
description: IT3（航海スケジュール・経路候補算出）の KPT ふりかえり
date: 2026-07-29
---

# イテレーション 3 ふりかえり（KPT）

対象: IT3（US24 航海スケジュール登録、US25 更新、US07 検索、US08 経路候補算出）。目標 13SP / 実績 13SP（達成率 100%）。

## サマリー

| 指標 | 値 |
| :--- | :--- |
| 目標 SP / 実績 SP | 13 / 13 |
| テスト | 251 件 green（38 ファイル） |
| カバレッジ | 全体 statements 94.38%、branches 82.84%、Routing domain 93.91% |
| SonarQube Quality Gate | PASS（new coverage 91.9%、new violations 0） |
| CI | local green。remote CI は IT3 クローズコミット push 後に確認 |
| レビュー | XP 5 視点レビュー実施。High 8 件はクローズ内対応または ADR 方針化 |

## Keep（継続すること）

- **中盤インサイドアウト**が機能した。`Voyage` / `Schedule` / `RouteCandidateFinder` の不変条件を domain test から固めたことで、UI 追加後も中心ルールが崩れなかった。
- **BC 独立性の機械検証**を維持できた。Routing 追加後も dependency-cruiser は no violation。
- **レビュー後の受入基準補強**が効果的だった。複数寄港地入力、出発期間検索、候補算出導線、必須入力エラーを E2E で固定した。
- **外部経路 Port + fallback**により、外部サービス未接続でも業務デモを止めない構造を作れた。

## Problem（問題点）

- 初回実装では US24/US07/US08 の UI 受入基準の一部を満たしていなかった。特に「検索から候補算出へ進む」操作は、HTTP endpoint があるだけでは利用者価値にならない。
- ADR-007 の「スタブ ACL 返済」の表現が粗く、Routing Context の経路候補と Estimation Context の見積候補が同じ Port かのように読めた。
- QueryService は N+1 とメモリ側絞り込みを残しており、検索件数増加に備えた SQL 化が必要。
- 外部 `fetch` の timeout がないため、遅延障害時の fallback 到達性が弱い。
- 開発 index / root index / mkdocs nav の更新が後追いになり、完了状態の入口が一時的に矛盾した。

## Try（次に試すこと）

| # | アクション | 期待効果 | 反映先 |
| :--- | :--- | :--- | :--- |
| T1 | 画面を伴う US は「endpoint がある」ではなく「対象ロールが画面操作で完結できる」を DoD に入れる | 受入基準の UI 導線漏れを防ぐ | IT4 opening |
| T2 | `RoutingCandidateController` の候補算出組み立てを `FindRouteCandidatesService` へ移す | Presentation の責務を薄くし、経路確定へ接続しやすくする | IT4 |
| T3 | `HttpExternalRoutingService` に timeout / abort を追加し、fallback test に遅延ケースを含める | 外部サービス遅延時の品質を担保 | IT4 |
| T4 | Voyage 検索を join / aggregate query 化し、出発地・目的地・期間を DB 側で絞る | N+1 と件数増加リスクを抑える | IT4 以降 |
| T5 | Estimation 見積候補と Routing 経路候補の Port 境界を ADR-008 で整理する | ADR-007 の段階移行判断を明文化 | IT4 |
| T6 | 更新確認画面へ進む前に日付逆転・寄港地時系列を共通 validation で止める | 利用者が invalid diff を確認する UX を避ける | IT4 |
| T7 | Repository 統合テストの Testcontainers smoke を CI に追加するか、ADR-004 の pg-mem 適用範囲を更新する | DB 互換性リスクを明確化 | CI 改善 |

## 次イテレーション（IT4）への引き継ぎ

- **スコープ**: US09/US10/US11/US12/US13/US14。経路候補を選択し、予約へ紐付け、予約確定・追跡番号発行へ進める。
- **持ち越し**: US04 見積連携、Estimation 見積候補 Port の扱い、外部 routing timeout、検索 SQL 化。
- **重点**: IT3 で作った Routing 候補を Booking の `ROUTE_PROPOSED` 遷移へ接続する。画面導線は対象ロールの操作完結を必ず E2E で確認する。
