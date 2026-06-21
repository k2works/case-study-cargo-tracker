---
title: イテレーション 3 完了報告書
date: 2026-06-21
---

# イテレーション 3 完了報告書

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT3 |
| 期間 | 2026-07-20 〜 2026-08-02（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | 航海スケジュール検索（US07）と経路候補算出（US08、Phase 2 最大リスク要素）を完成させる。IT2 申し送りの技術的負債を解消し new_coverage 80% を復元する |
| 計画 SP | 11（US07: 3 + US08: 8） |
| 実績 SP | 11 |
| 達成率 | 100% |

## ストーリー実績

| ID | ストーリー | 状態 | 計画 SP | 実績 SP |
|----|-----------|------|---------|---------|
| US07 | 航海スケジュールを検索する | ✅ 完了 | 3 | 3 |
| US08 | 経路候補を算出する | ✅ 完了 | 8 | 8 |
| **合計** | | | **11** | **11** |

## タスク実績

24 タスクすべて完了（IT2 申し送り 0.x: 15 件、US07: 1.1-1.6: 6 件、US08: 2.1-2.9: 9 件）。

### IT2 申し送り（0.x）

| # | タスク | 完了内容 |
|---|--------|---------|
| 0.1 | Controller 統合テスト追加 | Booking/Estimate/Shipper/Voyage/Home の FakeRequest + CSRF + auth + flash 経路を網羅 |
| 0.2 | 楽観ロック活性化 | Cargo / Estimate / Shipper / Voyage に version + WHERE 句楽観ロック + OptimisticLockException |
| 0.3 | VoyageCommandService 重複抽出 | `upsert(vn, existence, build)` 共通骨格に統合 |
| 0.4 | sealed エラー網羅 match | BookingCommandService.book のエラー網羅 |
| 0.5 | scoverage Twirl 調査 | 既に解消済み。`sbt clean coverage test` で statement 86.28% / branch 77.33% を確認 |
| 0.6-0.15 | Dashboard / htmx / Scalacheck 他 | IT2 までで完了済み |

### US07 航海スケジュール検索

| # | タスク | 完了内容 |
|---|--------|---------|
| 1.1 | ADR 0006 | voyage 拡張 + voyage_supported_cargo_type 中間テーブル + Routing 用 RouteCandidate/RoutingLeg 値オブジェクト分離 |
| 1.2 | Flyway V8 | vessel_name / carrier_code / 中間テーブル追加 |
| 1.3 | Voyage 拡張 + findByCriteria | vessel/carrier/supportedCargoTypes 拡張、`VoyageRow` 経由で EXISTS 条件を組み立て |
| 1.4 | VoyageQueryService.search | LocalDate → Instant 拡張、UnLocode / CargoType バリデーション |
| 1.5 | 検索画面 | views/voyage/search.scala.html + Controller 連携 |
| 1.6 | テスト | QueryService 7 / Repository 5 / E2E 4 件すべて緑 |

### US08 経路候補算出

| # | タスク | 完了内容 |
|---|--------|---------|
| 2.1 | Spike 格上げ | `RouteCandidateSearch` を application 層に昇格、`RoutingLeg` / `RouteCandidate` を routing.domain に配置 |
| 2.2 | 隣接リスト最適化 | `legs.groupBy(_.from)` で O(V+E) 探索 |
| 2.3 | PricingService 連携 | `PricedRouteCandidate(candidate, estimatedCost)` でレグ別単価を Money 合算 |
| 2.4 | 貨物種別フィルタ | `toRoutingLegs(voyages, cargoTypeFilter)` |
| 2.5 | topN | (区間数, 所要日数, 出港時刻) でソート |
| 2.6 | 期限内不到達通知 | Controller 側で on-time / late に分離し画面で動的アラート + 条件緩和ガイド |
| 2.7 | Controller + 画面 | `GET /bookings/:bookingId/routes` + Twirl |
| 2.8 | パフォーマンステスト | 航海 1000 件 / 20 試行で P95 = 60ms（上限 3 秒の 1/50） |
| 2.9 | 統合 + E2E | 直行 / 危険物フィルタ / 期限超過の 3 シナリオが緑 |

## バーンダウン

```mermaid
xychart-beta
    title "IT3 バーンダウン（実績）"
    x-axis ["開始", "Day 1"]
    y-axis "残 SP" 0 --> 11
    line "計画" [11, 0]
    line "実績" [11, 0]
```

## 品質メトリクス

| 指標 | 結果 |
|------|------|
| テスト件数 | 222 件（45 Suites）/ 全件成功 |
| Statement coverage | 86.28% |
| Branch coverage | 77.33% |
| 経路探索 P95 | 60ms（非機能要件 3 秒の 1/50） |
| ArchUnit 違反 | 0 件（CalculateRouteCommand / SearchVoyageCommand / PricedRouteCandidate を queryservices → application 直下に移動して解消） |

## 主要な実装上の意思決定

- ADR 0006: `voyage` テーブルに船名・運送会社を直接追加し、対応貨物種別は中間テーブルで多対多管理。Routing 用 `RouteCandidate` を Estimation 既存型と区別。
- 楽観ロックは `Either[DomainError, A]` ではなく `OptimisticLockException` 投擲方式で活性化（Play 標準のエラーハンドラと整合）。
- 経路探索の上位 N ソートは「区間数（直行優先） → 所要日数 → 出港時刻」。料金スコアリングは合計金額として候補に同梱するが並び順には未反映（IT4 以降で重み付け検討）。
- Scala 3 ArchUnit ルール（queryservices トップレベルは `*QueryService` 命名）に合わせ、コマンド/値オブジェクトを `routing.application` 直下へ分離。

## 申し送り（IT4 以降）

- 楽観ロックを `Either[DomainError.ConcurrentModification, A]` API に置き換える検討（現状は例外）
- 料金スコアリングを topN 並び順に反映（重み付け or pareto）
- 経路候補画面の htmx 部分更新化（現状は全画面 GET）

## 関連ドキュメント

- [IT3 計画](./iteration_plan-3.md)
- [IT2 完了報告書](./iteration_report-2.md)
- [ADR 0006 Voyage データモデル拡張](../adr/0006-voyage-data-model-extension.md)
