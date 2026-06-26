# 分析成果物 整合性セルフレビュー (2026-06-26)

## 概要

Haskell 版 take-1 の分析フェーズ全 13 ドキュメント (戦略 2 + 要件 4 + 設計 10 + ADR 1 + 開発計画 1) が完成した時点でのセルフ整合性レビューの結果を記録する。
多視点レビュー (`developing-review` / `analyzing-review` で XP エージェント並列起動) を実施する前段の、軽量機械的検証として位置付ける。

## レビュー方法

`grep` ベースで以下の対応関係を機械的に検査:

1. ドメインモデルの集約 ↔ データモデルのテーブル
2. ユースケース ID ↔ ユーザーストーリー ID ↔ リリース計画
3. ロール定義 (architecture_backend ↔ ui_design)
4. 主要ライブラリ (architecture_backend ↔ tech_stack)
5. 状態列挙 (BookingStatus 等) の domain-model ↔ data-model 整合

## 検証結果

### ✅ ドメインモデル ↔ データモデル

7 集約ルートすべて対応テーブルあり。

| 集約 | テーブル | 状態 |
| :--- | :--- | :---: |
| `Cargo` | `cargo` + `leg` | ✅ |
| `Shipper` | `shipper` | ✅ |
| `Voyage` | `voyage` + `carrier_movement` | ✅ |
| `TrackingActivity` | `tracking_activity` + `tracking_handling_event` + `tracking_exception_event` | ✅ |
| `HandlingActivity` | `handling_activity` + `customs_declaration` | ✅ |
| `Invoice` | `invoice` + `invoice_line_item` | ✅ |
| `Estimate` | `estimate` + `route_candidate` | ✅ |

横断的補助テーブル:

| テーブル | 用途 | 状態 |
| :--- | :--- | :---: |
| `location` | Shared Domain | ✅ |
| `users` / `user_roles` | 認証 | ✅ |
| `route_candidate_selection` | US09 経路選択記録 | ✅ |
| `notification_log` | US12 / US13 通知ログ | ✅ |

### ✅ UC ↔ US ↔ リリース計画

| 項目 | カウント | 状態 |
| :--- | :---: | :---: |
| ユースケース (system_usecase.md) | UC01〜UC19 | ✅ |
| ユーザーストーリー (user_story.md) | US01〜US25 | ✅ |
| リリース計画でカバー | US01〜US25 全 25 件 | ✅ 漏れなし |
| 横断要件 (認証) | 別途 IT1 に組み込み | ✅ |

### ✅ ロール定義

architecture_backend / ui_design ともに 7 ロール (`Shipper` / `Sales` / `RouteDesigner` / `Handler` / `Tracker` / `Accountant` / `Admin`) で一致。

### ✅ 主要ライブラリ

architecture_backend と tech_stack で言及される主要ライブラリ (Servant, Warp, Lucid, postgresql-simple, ReaderT, aeson, katip) が一致。
`servant-auth` は tech_stack でのみ言及 (アーキテクチャでは「Servant Auth による認証・認可」と表記) — 表記揺れだが概念的に同一。

### ✅ 状態列挙

`BookingStatus` の 9 値 (`Preliminary` / `RouteProposed` / `RouteAssigned` / `Confirmed` / `TrackingIssued` / `InTransit` / `Delivered` / `Settled` / `Cancelled`) が domain-model に明記され、data-model の `booking_status VARCHAR(30) NOT NULL DEFAULT 'PRELIMINARY'` 制約に対応。

## 検出事項

### 軽微な指摘

| ID | 指摘 | 重要度 | 対応状況 |
| :--- | :--- | :---: | :--- |
| C-01 | `servant-auth` の表記が architecture_backend では「Servant Auth」、tech_stack では `servant-auth-server` と異なる | 低 | ✅ 対応済 (architecture_backend を `Servant Auth (servant-auth-server)` に修正) |
| C-02 | data-model の `BookingStatus` / `cargo_type` / `transport_status` / `event_type` (tracking_handling_event) CHECK 制約が DDL に未記述 | 低 | ✅ 対応済 (4 カラムに CHECK 制約を追加) |
| C-03 | `notification_log.type` の値リスト (`RouteNotified` / `BookingConfirmed` / `BookingCancelled` / `LostEscalated`) が domain-model の DomainEvent と直接対応していない | 低 | 未対応 (開発時に NotificationKind と対応表整理) |

### 検出されなかった重大問題

- 集約とテーブルの食い違い: なし
- US カバレッジの漏れ: なし
- ロール定義の不一致: なし
- 主要ライブラリの欠落: なし
- 状態遷移の矛盾: なし (BookingStatus は domain-model の `canTransitionTo` テーブルと data-model の CHECK 制約候補が整合)

## 総合評価

✅ **分析フェーズの整合性は良好**。指摘事項は開発開始後のリファクタリング範囲で吸収可能。

開発フェーズへの移行に支障なし。

## 推奨される追加レビュー

本セルフレビューは grep ベースの機械的検証に留まる。以下を追加で検討:

| レビュー | スキル | 重要度 |
| :--- | :--- | :---: |
| 多視点レビュー (XP プロダクトマネージャー・アーキテクト・テスター・ユーザー代表) | `analyzing-review` | 中 |
| マルチパースペクティブ レビュー (Mermaid 図表・ドメインモデル妥当性) | `analyzing-review` | 中 |
| UI/UX レビュー (Lucid + htmx 設計の使いやすさ) | `developing-uiux-review` | 低 (開発前) |

## 参照

- [リリース計画](../development/release_plan.md)
- [ドメインモデル設計](../design/domain-model.md)
- [データモデル設計](../design/data-model.md)
- [ユーザーストーリー](../requirements/user_story.md)
