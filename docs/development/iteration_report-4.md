---
title: イテレーション 4 完了報告書
description: IT4（US08 経路候補算出・US09 経路確定・IT3 Try 全返済）の完了報告書。
tags: development, iteration-report, iteration-4, go
---

# イテレーション 4 完了報告書

## エグゼクティブサマリー

IT4 は中盤局面（インサイドアウト）の核心として、経路候補算出（US08・8 SP）と経路選択・確定（US09・3 SP）を計画どおり 11 SP 完遂した。最難関の経路探索を `RouteFinder` ドメインサービスとして domain 層に隔離し、Voyage 群を接続グラフとみなす DFS で直行優先・推奨順の候補算出を実現。BC 横断（Routing 探索 → Booking 経路確定 / Estimation 見積）は合成ルート注入方式（ADR-0007）で `.go-arch-lint.yml` を無改変に保ったまま実現した。IT3 の Try（T1-T6）を全返済し、持ち越しゼロ。品質ゲート（make check・CI・SonarQube Quality Gate）はすべて PASS。

## 達成状況

| ストーリー | SP | 状態 |
|-----------|----|----|
| US08 経路候補を算出する | 8 | ✅ 完了 |
| US09 経路を選択・確定する | 3 | ✅ 完了 |
| **合計** | **11** | **100%** |

IT3 Try 返済: T1（設計同時反映）・T2（動的区間・edit 全区間）・T3（見積実経路化・Clock 注入）・T4（アクセシビリティ）・T5（境界テスト補強）・T6（Estimate 不変条件）をすべて返済。

### 成功基準

- ✅ US08/US09 の受け入れ基準を満たす（該当なし通知・直行優先・推奨順・確定→ROUTED）。
- ✅ 経路探索を domain のドメインサービスとして隔離検証（直行/経由/接続不能/期限超過/貨物種別非対応/期限ちょうど/循環回避/推奨順タイブレーク）。
- ✅ Routing・Booking のドメイン層カバレッジ 90% 以上（routing 96.6%・booking 96.4%）、SonarQube Quality Gate PASS。
- ✅ `make check`（build + test + lint + govulncheck + arch）green・CI success。

## 技術的成果

### 実装

- **Routing 経路探索（US08）**: `RouteSpecification`/`Leg`/`RouteCandidate` 値オブジェクトと `RouteFinder` ドメインサービス（空間連結・時刻連続・貨物種別対応・到着期限内を評価する DFS・直行優先の推奨順ソート・maxRouteLegs=4 の深度制限・循環回避）。application `SearchRoutesService`（公開 DTO `RouteCandidateView`）。
- **Booking 経路確定（US09）**: `Leg`/`CargoItinerary`（空間・時刻の連結不変条件）/`Delivery`/`RoutingStatus`（shared）を追加。`Cargo.AssignItinerary`（ROUTE_PROPOSED 時のみ・routingStatus=ROUTED・BookingStatus 不変）。`leg` テーブル（000009）・sqlc・`SaveItinerary` のトランザクション永続化。application `RouteSearcher` ACL ポート・`AssignRouteService`。interfaces `/bookings/{bookingId}/route` 画面。
- **BC 横断オーケストレーション**: `cmd/server` の変換アダプタが Routing の `SearchRoutesService` を Booking/Estimation の `RouteSearcher` ポートへ写像（ADR-0007・go-arch-lint 無改変）。
- **Try 返済**: shared `Clock` ポート（time.Now 排除）。Estimation の実経路化（`route_candidate.waypoints` 000010・経由港表示）。航海フォームの区間動的追加・edit 全区間表示（データ損失解消）。Estimate 到着期限の不変条件（`NewEstimateParams`）。
- **UX 改善（クローズ時レビュー対応）**: 経路割り当て画面に到着期限・各候補の到着予定日/残日数（期限超過警告）・費用 3 桁区切り。予約詳細の確定経路に到着予定日・所要日数。

### コード規模

- 変更ファイル: 52（Go・SQL・テンプレート・E2E・docs）
- Go コード: 約 +2,361 行 / -74 行
- 新規マイグレーション: 000009（leg・cargo.routing_status）・000010（route_candidate.waypoints）
- 新規 ADR: ADR-0007

## 品質指標

| 指標 | 実績 |
|------|------|
| ドメイン層カバレッジ | routing 96.6% / booking 96.4% / estimation 91.5% / shared 95.8% |
| SonarQube Quality Gate | PASS（new_coverage 80.8% / 重複 0.48% / new_violations 0 / Bug 0 / Vulnerability 0） |
| make check | green（build + test + lint + arch） |
| golangci-lint | 0 issues |
| go-arch-lint | OK（BC 独立性維持・無改変） |
| govulncheck | 脆弱性なし |
| CI（Backend CI・go/take-1） | success |
| テスト | 単体・統合（testcontainers）・E2E（Playwright）すべて green |

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを 2 段（開発中の中間 self-review 3 観点 + クローズ時 5 観点統合）で実施（[IT4 レビュー](../review/it4_go_review_20260725.md)）。

- **高優先度（クローズ前に全対応）**:
  - 到着期限判定の DATE(00:00) vs TIMESTAMP(時分) 齟齬で期限当日着を誤って刈るバグ（programmer/tester 同時指摘）→ 日付単位比較に是正・境界テスト追加。
  - 経路割り当て画面に期限充足の判断材料（到着期限・到着予定日・残日数）が欠落（user-representative H1）→ 表示を追加。
  - data-model の物理カラム未同期（technical-writer M1/M2）→ cargo.routing_status・route_candidate.waypoints を反映。
- **繰越（IT5+）**: US10 導線・確定前確認・並び順可視化・sqlcgen 全スキーマ重複返済・多段探索深掘り。

## 課題と残作業

- **US10 経路条件調整（IT5）**: 該当なし通知からの再算出・条件緩和導線。route.html の行き止まり解消。
- **技術的負債**: sqlcgen 全スキーマ重複（ADR-0005 決定3・per-BC schema 分離で返済）、経路探索の多段乗り継ぎ深掘り・費用モデル精緻化（段階実装の後続）、候補選択の TOCTOU（programmer L-2・リスク顕在時）。
- **プロセス改善（ふりかえり Try）**: マイグレーション追加時の data-model 同時更新 DoD 化、受入基準 UX の E2E アサート化、feature ブランチ CI 手動トリガーの手順明記。

## 次イテレーション（IT5）への引き継ぎ

- IT5 の中心は US10（経路条件調整）。US08/US09 の運用完成として route.html の UX 改善（該当なし導線・確定前確認・並び順可視化）を同時に行う。
- 良好な状態の維持: BC 独立性（go-arch-lint 無改変）・ドメイン層 90%+ カバレッジ・合成ルート注入方式・2 段レビュー運用。

## 関連ドキュメント

- [IT4 イテレーション計画](iteration_plan-4.md)
- [IT4 ふりかえり](retrospective-4.md)
- [IT4 マルチパースペクティブレビュー](../review/it4_go_review_20260725.md)
- [ADR-0007 経路探索の BC 横断 ACL](../adr/0007-route-search-cross-bc-acl.md)
- [リリース計画](release_plan.md)
