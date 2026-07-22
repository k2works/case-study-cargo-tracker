---
title: イテレーション 2 完了報告書 - 航海スケジュール
description: IT2（US24/US25/US07）の達成度・指標・テスト結果・レビュー・評価
published: true
date: 2026-07-22T00:00:00.000Z
---

# イテレーション 2 完了報告書

## エグゼクティブサマリー

| 項目 | 内容 |
|------|------|
| **イテレーション** | 2（航海スケジュール） |
| **期間** | 2026-07-22（実績・単日集中） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP / 実績 SP** | 11 / 11 |
| **達成率** | 100%（機能スコープ） |
| **テスト** | ドメイン 16 + app 5 + Repository 統合 4 + HTTP フロー 11（+ US03/US05 の 4）＝全 green |
| **カバレッジ** | Routing 層 83〜91%（cargo-llvm-cov） |
| **主要成果** | 航海登録→更新→検索の縦切り（Routing Context）+ IT1 Try #1-#3 返済 + DIP 回復（ADR-0003）+ developing-review |

## 1. イテレーション概要

### 1.1 目的と背景

IT2 は中盤局面（インサイドアウト）の最初のイテレーションであり、Routing Context の Voyage 集約をドメイン層から堅牢に作り込み、航海スケジュールの新規登録（US24）・更新（US25）・検索（US07）を実 PostgreSQL 上で成立させることを目的とした。あわせて IT1 の開発成果物レビューで高優先度とされた技術的負債（認可の分散・DIP 逸脱・テスト網羅穴・カバレッジ未計測）を返済し、後続イテレーションの土台を整えた。

### 1.2 スコープ

| ID | ユーザーストーリー | SP | 結果 |
|----|-------------------|----|------|
| US24 | 航海スケジュールを新規登録する | 3 | 完了 |
| US25 | 既存航海スケジュールを更新する | 3 | 完了 |
| US07 | 航海スケジュールを検索する | 5 | 完了（出発期間検索・予約番号連携は IT3 へ） |
| **合計** | | **11** | |

## 2. 達成状況

### 2.1 ストーリー別受入条件

- **US24（新規登録）**: 航海番号・船名・運送会社・出発港/到着港（UN/LOCODE）・出発日時/到着日時・対応貨物種別の入力、寄港地の複数区間（2 区間）、必須未入力・日付逆転・重複の 422 エラー、登録後の検索対象化を実装・実証。
- **US25（更新）**: 航海番号指定での呼び出し、現在の登録内容カードによる差分確認、上書き更新、キャンセルで既存不変、更新内容の検索反映を実装・実証。
- **US07（検索）**: 出発港・到着港・貨物種別による絞り込み、危険物・冷凍の対応航海絞り込み、0 件時の再検索案内を実装・実証。**未実装**: 出発期間での検索、予約番号からの貨物仕様確認（IT3 の経路設計フローで対応）。

### 2.2 局面移行の一貫性

IT1（序盤・アウトサイドイン）から IT2（中盤・インサイドアウト）への移行にあたり、Red-Green-Refactor・1 コミット 1 変更・品質基準・ヘキサゴナル境界・ユビキタス言語の連続性を維持した。共有カーネル `Location`・認可・composition root のパターンを踏襲した。

## 3. 技術的成果

### 3.1 実装（レイヤー別）

| レイヤー | クレート | 主要成果物 |
|---------|---------|-----------|
| Domain | domain-routing | Voyage 集約、VoyageNumber/VesselName/Carrier/CargoType、CarrierMovement（エンティティ）、Schedule、VoyageRepository ポート |
| Infrastructure | infra-persistence | SqlxVoyageRepository（upsert・子テーブル再挿入）、migration（voyage/carrier_movement/voyage_cargo_type） |
| Application | app-routing（新規） | VoyageCommandService（登録/更新）、VoyageQueryService（一覧/検索/参照） |
| Interfaces | interface-web | 航路一覧/登録/更新画面、RoleGuard 認可 extractor |

### 3.2 アーキテクチャ上の意思決定

- **CargoType の BC 固有型化**: Routing Context 独自の CargoType を定義し、domain-booking への直接依存を回避（コンパイラで境界強制）。
- **対応貨物種別の正規化**: 1 航海が複数貨物種別に対応しうるため、`voyage_cargo_type` 子テーブルへ正規化。
- **認可の型安全 extractor 化（IT1 Try #1）**: `RoleGuard<R: RequiredRole>` により認可の書き忘れをコンパイラが構造的に防止。
- **DIP 回復（IT1 Try #2・ADR-0003）**: 各出力ポート trait に `Arc<dyn Trait>` のブランケット実装を追加し、composition root で sqlx 実装を注入。interface 層から `Sqlx*::new` を排除。

## 4. 品質指標

| 指標 | 実績 | 目標 | 判定 |
|------|------|------|------|
| ドメイン層カバレッジ（行） | 83〜91% | 85% | ほぼ達成 |
| 全テスト | 全 green（40 件相当） | green | 達成 |
| clippy（`-D warnings`） | クリーン | 0 警告 | 達成 |
| rustfmt | 準拠 | 準拠 | 達成 |
| ベロシティ | 11 SP | 10-12 SP | 達成（安定） |

cargo-llvm-cov を導入（IT1 Try）し、Routing 層のカバレッジを可視化。CI ゲートへの組込は IT3 で実施。

### コミット内訳（14 件）

feat 4 / refactor 2 / test 2 / docs 6。

## 5. レビュー結果

`developing-review` で 5 エージェント（programmer/tester/architect/technical-writer/user-representative）による並列レビューを実施（[it2_development_review_20260722.md](../review/it2_development_review_20260722.md)、高 6 / 中 6 / 低 5）。

- **対応済（高 1-4）**: US24 寄港地複数・日付逆転 422、US07 0 件、US25 キャンセルの受入基準テスト漏れを HTTP フローで補完（5 本追加）。中 8・低 13 も改善。
- **対応済（高 5-6）**: DIP 逸脱を ADR-0003 で回復。ui_design.md に航路登録・更新画面を反映。
- **後続（中）**: `search` の N+1 を SQL 絞り込みへ、CQRS 非対称性の判断固定。

## 6. 課題と残作業

- **US07 の一部未実装**: 出発期間検索・予約番号からの貨物仕様確認は IT3（US08 経路候補算出フロー）で対応。
- **`search` の三重 N+1**: 全件ロード + Rust フィルタを SQL WHERE ベースへ（IT3）。
- **`CurrentUser.roles` の Vec<Role> 型化**: ADR-0003 でスコープ外・別途。
- **実ブラウザ動作確認**: HTTP フローで代替。IT2 クローズ時に実施予定（IT1 教訓の設定系ランタイム不具合対策）。
- **cargo-llvm-cov の CI ゲート化**: IT3 の CI 整備とあわせて実施。

## 7. 次イテレーション（IT3）への引き継ぎ

IT3 は経路候補算出（US08）・経路選択確定（US09）を実装する。以下を引き継ぐ。

- US07 の未実装分（出発期間検索・予約番号連携）を US08 の経路設計フローに統合
- Voyage 検索 Repository の SQL 絞り込み化（N+1 解消）
- CI 整備（カバレッジゲート・`cargo sqlx prepare --check`）
- infra-eventbus 骨格は IT4-5（Booking/Tracking 連携）で完成させる方針を維持

## 更新履歴

| 日付 | 更新内容 |
|------|---------|
| 2026-07-22 | 初版作成 |
