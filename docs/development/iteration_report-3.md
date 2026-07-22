---
title: イテレーション 3 完了報告書 - 経路算出・選択
description: IT3（US08/US09）の達成度・指標・テスト結果・レビュー・評価
published: true
date: 2026-07-22T00:00:00.000Z
---

# イテレーション 3 完了報告書

## エグゼクティブサマリー

| 項目 | 内容 |
|------|------|
| **イテレーション** | 3（経路算出・選択） |
| **期間** | 2026-07-22（実績・単日集中） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP / 実績 SP** | 11 / 11 |
| **達成率** | 100%（機能スコープ） |
| **テスト** | domain-routing 28 + app-routing 9 + Repository 統合（CargoSpecProvider/SelectedRouteRepository/search）+ HTTP フロー 6＝全 green |
| **カバレッジ** | domain-routing 92.45% lines（route.rs 97.56%、CI ゲート 85% 突破） |
| **主要成果** | 予約起点の経路設計フロー（DFS 経路探索→候補提示→確定・永続化）+ CI 品質ゲート導入 + IT2 Try 着手 + developing-review |

## 1. イテレーション概要

### 1.1 目的と背景

IT3 は中盤局面（インサイドアウト）の 2 番目のイテレーションであり、Routing Context の最難関ストーリーである経路候補算出（US08）を、制約充足を伴う DFS 経路探索としてドメイン層から作り込むことを目的とした。あわせて経路選択・確定（US09）を実装し、予約起点（`/bookings/{bookingId}/route`）の経路設計フローを実 PostgreSQL 上で成立させた。IT2 のふりかえりで挙げた Try（受入基準×テスト 1:1・search の SQL 化・設計同時反映・CI 整備）の実践イテレーションでもある。

### 1.2 スコープ

| ID | ユーザーストーリー | SP | 結果 |
|----|-------------------|----|------|
| US08 | 経路候補を算出する | 8 | 完了（費用列・条件調整導線は後続 IT） |
| US09 | 経路を選択・確定する | 3 | 完了 |
| **合計** | | **11** | |

## 2. 達成状況

### 2.1 ストーリー別受入条件

- **US08（経路候補算出）**: 予約の貨物仕様（出発地・目的地・到着期限・貨物種別）を ACL（CargoSpecProvider）経由で取得し、直行・単純接続・多段接続（max_legs=3）を DFS で探索。サイクル回避・時系列連結（前区間の荷揚 ≤ 次区間の積載）・推奨順ソート（区間数→所要日数→期限内優先）・期限超過候補の ⚠ 表示・期限内 0 件時の通知を実装・実証。
- **US09（経路選択・確定）**: 候補選択→確定で `selected_route`/`selected_route_leg` へ永続化（upsert・子区間 wash-replace）、予約詳細へ 303 リダイレクト、存在しない予約の 404、範囲外候補の 422 を実装・実証。BookingStatus の RouteProposed 反映は US11（IT4）へ。

### 2.2 局面移行の一貫性

IT2（中盤・インサイドアウト）と同一局面のため、ドメイン層→infra→app→interface の積み上げ順・Red-Green-Refactor・1 コミット 1 目的・ヘキサゴナル境界・共有カーネル `Location`・`RoleGuard<R>` 認可・composition root 注入（ADR-0003）のパターンを一貫踏襲した。BC 独立（Routing 固有 `RouteLeg`/`CargoType`・Booking 非依存の ACL）を新規に確立した。

## 3. 技術的成果

### 3.1 実装（レイヤー別）

- **domain-routing**: `RouteLeg`（Routing 固有 VO）・`RouteCandidate`・`RouteCandidateCalculator`（DFS 探索）・`CargoType`（Routing 固有列挙）・ポート（`CargoSpecProvider`・`SelectedRouteRepository`・`CargoSpec` DTO・`AclError`）。
- **app-routing**: `RoutePlanningService<R,P,S>`（plan_routes/confirm_route/cargo_spec）、`VoyageServiceError` に InvalidDate/Acl/BookingNotFound/InvalidCandidate を追加。
- **infra-persistence**: `SqlxCargoSpecProvider`・`SqlxSelectedRouteRepository`、`search` を EXISTS 副問い合わせベースの SQL WHERE 絞り込みへ改修（IT2 Try #2）、`selected_route`/`selected_route_leg` マイグレーション。
- **interface-web**: `route_design`（GET）・`route_confirm`（POST）ハンドラ（`RouteDesignerUser` RoleGuard）、`route_design.html`（貨物仕様カード・候補テーブル・★推奨・⚠期限超過・0 件警告）。
- **CI**: `.github/workflows/rust-ci.yml`（lint・test・coverage ゲート 85% for domain-routing）。

### 3.2 アーキテクチャ上の意思決定

- **BC 独立の型強制**: Booking の `Leg` を流用せず Routing 固有 `RouteLeg` を定義。`CargoType` も Routing 固有列挙とし、Cargo.toml 依存宣言で Booking 非参照を強制。
- **ACL による貨物仕様取得**: `CargoSpecProvider` で Booking の cargo テーブルを Routing から疎結合に参照（BC 越境を ACL に閉じ込め）。
- **確定経路のスナップショット**: `selected_route` は cargo への FK を持たず、booking_id を業務キーとして保持（BC 独立・疎結合）。ADR 起票候補として IT4 へ。

## 4. 品質指標

| 指標 | 実績 |
|------|------|
| テストカバレッジ（domain-routing） | 92.45% lines（route.rs 97.56%、CI ゲート 85% 突破） |
| 全テスト | 全 green（domain-routing 28 / app-routing 9 / Repository 統合 / HTTP フロー 6・ワークスペース全体 green） |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠・CI パイプライン稼働 |
| ベロシティ | 11 SP（IT1=16 → IT2=11 → IT3=11、計画ラインと一致し安定） |

### コミット内訳（IT3 分）

- feat: 4（domain-routing 探索・app-routing ユースケース・interface-web 画面・US09 永続化）
- refactor: 1（search の SQL 化）
- test: 1（受入基準実証テスト補強）
- ci/docs: CI 品質ゲート・設計反映・受入基準対応表・計画/レビュー/進捗/ふりかえり

## 5. レビュー結果

`developing-review`（5 エージェント並列）を実施し、統合レポートを [it3_development_review_20260722.md](../review/it3_development_review_20260722.md) に記録（高 3 / 中 5 / 低 4 + 業務 4 + ADR 3）。高優先度 3 件（経路 0 件 web テスト・探索境界テスト・422 web テスト）はクローズ前に補完し受入基準を実証した。中優先度・業務指摘（find_all 全件取得・期限超過候補の選択可否・費用列・TOCTOU）は IT4/後続の Try・ADR に整理した。

## 6. 課題と残作業

- **plan_routes の find_all 全件取得**: search は SQL 化したが探索本体は全件ロード。cargo_type での SQL 絞り込みが IT4 Try #2。
- **confirm_route の TOCTOU**: 表示時と確定時の再算出で候補ずれの可能性。候補同一性照合を IT4 Try #4 + ADR で対応。
- **業務判断軸**: 費用列（Estimation 連携）・期限超過候補の選択不可化（誤確定防止）・条件調整導線（US10）が後続 IT。
- **相関副問い合わせの CTE 化**: search の可読性・性能改善（IT4 以降）。

## 7. 次イテレーション（IT4）への引き継ぎ

- **IT4 スコープ**: US10（経路条件調整・再算出）・US11（経路情報を予約に紐付け）・US12（確定経路の荷主通知）・US06（予約情報の経路設計者引き渡し）。US11 で `SelectedRouteRepository::exists`（現状未使用）を活用し確定経路を予約詳細に表示。
- **ADR 起票**: 経路探索アルゴリズムの範囲（内製 DFS + 外部連携後続化）・確定経路スナップショット方針・CI カバレッジゲートの段階拡大を正式起票。
- **infra-eventbus 骨格**: Booking/Tracking 連携が始まる IT4-5 で完成させる。

## 更新履歴

| 日付 | 更新内容 |
|------|---------|
| 2026-07-22 | 初版作成（IT3 クローズ） |
