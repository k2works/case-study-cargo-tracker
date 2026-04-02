---
title: イテレーション 2 ふりかえり
description: IT2 の Keep / Problem / Try を整理し、IT3 へ反映するためのふりかえり記録。
published: true
date: 2026-04-02T00:00:00.000Z
tags: retrospective, it2
---

# イテレーション 2 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT2 |
| 計画期間 | 2026-04-14 〜 2026-04-27 |
| 実績期間 | 2026-04-01 〜 2026-04-02 |
| 対象ストーリー | US01 / US06 |
| 計画 SP | 10 |
| 実績 SP | 10 |
| テスト件数 | 239 件（全 Green） |
| カバレッジ | 89.8% |
| SonarQube | Quality Gate 修正済み ✅ |

## Keep

### 技術面

- ヘキサゴナルアーキテクチャ（ポート＆アダプター）のパターンが IT1 で確立されており、IT2 でも同一構造で `quote`・`routing` コンテキストを迷いなく追加できました。
- DDD の集約・値オブジェクト・ドメインサービスの設計パターンが定着し、`Quote`・`RouteCandidate`・`QuoteCondition` を高凝集・低結合で実装できました。
- `Optional<RouteSearchService>` を使った `product` プロファイル互換パターンにより、stub 環境と本番環境の切り替えを Bean 注入レベルで安全に制御できました。
- SonarQube Quality Gate の違反（`@ApiResponses` ラッパー・ワイルドカード型）を同イテレーション内に修正し、品質ゲートを維持できました。
- Playwright Page Object Model（`QuotePage`・`BookingPage`・`RoutingPage`）の整備により、E2E テストの再利用性と保守性が向上しました。
- IT1 の Try 事項（`SecurityConfigTest` との連携・E2E と受入条件のひも付け）を IT2 で実践し、品質ベースラインを維持できました。

### プロセス面

- TDD サイクル（Red → Green → Refactor）を 12 タスクで一貫して実施し、テスト先行で機能を安定させてからリファクタリングする流れが定着しました。
- コミットを機能単位に細分化（`feat` / `test` / `fix` / `refactor` / `docs`）し、変更の追跡と後追いロールバックが容易になりました。
- SonarQube スキャン → Quality Gate 確認 → 修正コミット のサイクルを同イテレーション内で完結させました。
- ドキュメント更新（`iteration_plan-2.md` / `release_plan.md` / `retrospective-2.md`）をタスク 2.6 として明示的に計画に含め、後追い作業を防止できました。

## Problem

### 設計・実装

- `ObjectMapper` が `@SpringBootTest` のフルコンテキストで Bean として登録されない問題（Spring Boot 4 の挙動変更の可能性）に遭遇し、E2E テストの設計を `Location` ヘッダーから ID を抽出する方式に変更しました。テスト設計の柔軟性は得られましたが、Spring Boot 4 特有の挙動調査コストが発生しました。
- `StubRouteProviderAdapter` の `estimatedArrival` 計算（`requestedArrivalDate - 1 or -2`）が常にフィルタを通過するため、希望着日フィルタの境界テストが実質機能していません。stub の設計をより現実的な条件に近づける余地があります。
- `QuoteRestController` に `@ApiResponses` ラッパーが残ったまま（既存 Code Smell）で、新規実装の `RoutingRestController` で同じパターンを踏んでしまいました。既存コードのパターンを踏襲する前に SonarQube イシューを確認する習慣が必要です。

### 品質管理

- SonarQube の `new_violations` が 2 件発生し、コミット後のスキャンで発覚しました。コミット前のローカル lint・静的解析と SonarQube 事前チェックの仕組みがなく、違反が後から判明するサイクルになっています。
- テスト実行時に `7 failed` と表示される事象が発生しましたが、再実行で全 Green になりました。テストの実行順序依存またはコンテナ起動のタイミング問題の可能性があります。

## Try

| Try | 担当 | 期限 | 期待効果 |
|-----|------|------|----------|
| 新規コントローラー実装時に既存の SonarQube イシュー（`@ApiResponses` ラッパー・ワイルドカード型）を参照してから設計するルールを DoD に追加する | Copilot | IT3 開始時 | new_violations の発生を事前防止できる |
| `StubRouteProviderAdapter` の `estimatedArrival` を「希望着日より前・後・同日」の 3 パターンを返す実装に変更し、フィルタ境界テストを有効化する | Copilot | IT3 タスク開始前 | 日付フィルタの実効性を確認できる |
| テスト実行時の flaky 事象（順序依存・コンテナ起動タイミング）を調査し、`@DirtiesContext` または Testcontainers の `@BeforeEach` リセットを検討する | Copilot | IT3 実装中 | 再現性のある安定したテスト実行が確保できる |

## 次イテレーションへの引き継ぎ

- IT3 では US05（危険物・冷凍貨物予約）/ US07（ルート選択・予約紐付け）/ US08（予約確定）/ US09（追跡番号発行）を実装します。
- 品質ベースラインは backend 239 テスト Green、E2E 17 シナリオ Green、SonarQube Quality Gate PASS を維持条件とします。
- IT2 で整備した `routing` コンテキストの `RouteSearchService` を活用し、US07 のルート選択機能（`Booking` への `Route` 紐付け）を実装します。
- IT1 の Try で挙げた「E2E シナリオをストーリー受入条件にひも付けて管理」は IT2 で実践済み。IT3 でも同パターンを継続します。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-04-02 | IT2 ふりかえりを作成 | Copilot |
