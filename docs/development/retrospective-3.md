---
title: イテレーション 3 ふりかえり
date: 2026-06-21
---

# イテレーション 3 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| 期間 | 2026-07-20 〜 2026-08-02（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | 航海スケジュール検索（US07）と経路候補算出（US08、Phase 2 最大リスク要素）を完成させ、IT2 申し送りの技術的負債を解消し new_coverage 80% を復元する |
| 計画 SP | 11（US07: 3 + US08: 8） |
| 実績 SP | 11（100%） |

## 達成事項

- **US07（3 SP）**: ADR 0006、Flyway V8（voyage 拡張 + voyage_supported_cargo_type 中間テーブル）、Voyage 集約に船名 / 運送会社 / 対応貨物種別、`VoyageRepository.findByCriteria`、`VoyageQueryService.search`、検索画面 + 結果一覧 + 条件緩和ガイド
- **US08（8 SP）**: Spike を `RouteCandidateSearch` に格上げ、隣接リスト最適化、貨物種別フィルタ、topN ソート、`PricingService` 連携で料金見積もり付与、`/bookings/:bookingId/routes` 画面、期限内/期限超過の分離通知、Voyage 1000 件で P95=60ms のパフォーマンステスト、直行/中継/不到達/危険物/上位 N の E2E 統合
- **IT2 申し送り 15 件**: Controller 統合テスト、楽観ロック活性化、VoyageCommandService 重複抽出（upsert 骨格）、sealed エラー網羅 match、Dashboard pure 切り出し、htmx 動的フィールド、温度管理条件表示、ScalaCheck プロパティテスト、ScannerWork QG 構成、IT2 review 中優先 3 件 等
- **IT3 末期の追加対応**: Estimate findById SELECT 句の version 取り忘れ修正 + 「1 件 seed → list」回帰テスト追加、Voyage 側にも同型回帰テスト横展開、Dashboard「経路設計を開始」ボタンのリンク先を経路候補画面に修正、Estimate.reconstruct を `RouteSpec` / `CargoSpec` 集約に再構成し SonarQube Code Smell 解消

### 品質メトリクス

| 指標 | 結果 |
|------|------|
| テスト件数 | 224 件 / 全件成功（45 Suites） |
| Statement coverage | 89.39%（scoverage） |
| Branch coverage | 77.33% |
| SonarQube カバレッジ | 88.0% / new_coverage 84.9% |
| Code Smell | 0 / Bug 0 / Vulnerability 0 / Security Hotspot 0 |
| 重複率 | 0.0% |
| 経路探索 P95 | 60ms（非機能要件 3 秒の 1/50） |
| Quality Gate | PASS |

## KPT

### Keep（継続したいこと）

- **Spike → 本実装の段階的格上げ**: IT2 で純関数 Spike を `RouteCandidateSearch` として application 層に昇格する流れがスムーズで、隣接リスト最適化・貨物種別フィルタ・topN を段階追加できた。リスク要素は早めに Spike で叩いておくと本実装が見積もり通りに進む
- **マルチパースペクティブレビューの併用**: 実装完了直後に xp-programmer / tester / architect / technical-writer / user-representative の 5 視点並列レビューを回したことで、業務導線（user-rep）・テスト穴（tester）・命名規約逃避（architect）・計画書 vs 実装乖離（writer）が同時に検出された
- **SonarQube Quality Gate を毎イテレーション末に通す**: IT3 で `Estimate.reconstruct` のパラメータ過多が検出され、`RouteSpec` / `CargoSpec` 集約への再構成という DDD 的に望ましい改善につながった
- **「1 件 seed → list」回帰テストパターン**: SELECT 句のカラム追加忘れで本番が 500 になった事象から、空テーブル状態では検出できない穴を塞ぐ汎用パターンを発見・横展開できた
- **iteration_plan-3.md の Day 単位スケジュール**: タスクが多い（24 件）状況でも 1 日完結の AI ペアプロで全消化できた

### Problem（改善したいこと）

- **計画書と実装の乖離**: タスク 0.2「`Either[DomainError.ConcurrentModification, A]` を返す」と書きつつ実装は `OptimisticLockException` 投擲方式。完了マーク時に計画書の文言更新を忘れていた（IT3 末期にレビューで検出 → 修正済み）
- **業務導線の最終リンク漏れ**: 経路候補画面 `/bookings/:id/routes` を実装しながら、ダッシュボード「経路設計を開始」ボタンが旧来の `VoyageController.list` を指したまま残っていた。E2E はこの導線を通っていなかったので検出できず、ユーザーから「経路設計ボタン押したら航路一覧に飛ぶ」指摘で発覚
- **テストの「偽 stub」が混入**: `RouteCandidateQueryServiceSpec` の `InMemoryVoyageRepository.findByCriteria` が引数を無視して全件返す実装になっており、QueryService がフィルタを Repository に委譲しているかを検証できない状態
- **ArchUnit ルール 4 回避の判断が ADR 化されていない**: `CalculateRouteCommand` / `SearchVoyageCommand` / `PricedRouteCandidate` を queryservices から application 直下に移動した経緯が、コミットメッセージにのみ残っており設計判断として明文化されていない
- **計画書の文書品質**: ADR 0006 と iteration_plan-3.md L344 で VARCHAR 桁数（200 vs 100）の不一致、L601-604 に ADR 0006 表の二重掲載などコピペ残骸

### Try（次イテレーションで試したいこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|---------|
| T1 | IT4 計画策定時に「業務導線 E2E（ダッシュボード→候補画面→経路選択確定）」を Definition of Done に追加 | AI Agent | IT4 計画作成時 | リンク漏れ・選択確定アクション欠落の再発防止 |
| T2 | テスト用 InMemory Repository は「契約テスト」（同じ Spec を In-Memory と Scalike の両方で実行）パターンを導入 | AI Agent | IT4 | 偽 stub の検出、本物との振る舞い乖離を即検知 |
| T3 | 計画書のタスク完了時に「実装と計画の差分」セルフチェックを Definition of Done に追加 | AI Agent | IT4 | 計画書 vs 実装乖離の早期発見 |
| T4 | ArchUnit ルール変更や DDD 配置の判断は ADR として残す運用に統一 | AI Agent | IT4 | 設計判断のトレーサビリティ確保 |
| T5 | 表示フォーマッタ層（Money / Instant / UnLocode）を IT4 で追加し、業務感覚と一致する表示に統一 | AI Agent | IT4 | UX 改善、レビュー指摘 #3 解消 |
| T6 | 楽観ロックを `Either[DomainError.ConcurrentModification, A]` に統一する ADR を IT4 で起案 | AI Agent | IT4 〜 IT5 | 例外貫通の解消、レビュー指摘 #10 解消 |

## ベロシティ分析

| イテレーション | 計画 SP | 実績 SP | 達成率 | 累積ベロシティ |
|--------------|---------|---------|--------|--------------|
| IT1 | 12 | 12 | 100% | 12 |
| IT2 | 12 | 12 | 100% | 12 |
| IT3 | 11 | 11 | 100% | 11.67（直近 3 IT 平均）|
| **累積** | **35** | **35** | **100%** | — |

3 イテレーション連続 100% 達成。AI ペアプロ環境下では人間チームの「3 イテレーションでベロシティ安定」原則が成り立たないため、SP は「Java 版実績相当 + Scala 移行係数」の名目値として扱う運用が継続できている。

## 次のステップ

1. IT3 末期で検出された業務導線（経路選択確定）と表示フォーマッタを IT4 タスクとして積む
2. マルチパースペクティブレビュー高優先度 8 件のうち、IT3 内で対応済（#4 / 部分対応 #1）以外を IT4 計画に取り込み
3. `/planning-releases --iteration 4` で IT4 計画作成（Phase 2 後半、US09 経路確定 + Routing 関連の業務導線完成）
4. GitHub Project（#30）に IT3 完了分を `/syncing-github-project --sync` で反映

## 関連ドキュメント

- [IT3 計画](./iteration_plan-3.md)
- [IT3 完了報告書](./iteration_report-3.md)
- [IT3 マルチパースペクティブレビュー](../review/it3_implementation_review_20260621.md)
- [IT2 ふりかえり](./retrospective-2.md)
- [リリース計画](./release_plan.md)
