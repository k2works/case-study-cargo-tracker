---
title: イテレーション 1 完了報告書
description: IT1（ウォーキングスケルトン + 認証 + 荷主登録）の完了報告
date: 2026-07-28
---

# イテレーション 1 完了報告書

## エグゼクティブサマリー

IT1 では、国際貨物輸送管理システム（TypeScript 版）の**ウォーキングスケルトン**を構築し、その上で**認証（US26/US27）**と**荷主登録（US02/US03）**を実装した。目標 8SP を 100% 達成し、全ユーザーストーリーの受入基準を満たした（レビュー指摘の高優先度も本イテレーション内で対応）。品質ゲート（132 テスト green・Playwright E2E 5 件・SonarQube Quality Gate PASS・CI success）をすべて通過している。

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-07-27 〜 2026-07-28 |
| 目標 SP / 実績 SP | 8 / 8（達成率 100%） |
| 対象ストーリー | US26・US27・US02・US03 |
| コミット数 | 17 |
| 変更規模 | 89 ファイル / +13,511 行 |
| 実装ファイル | src 配下 55 ファイル（TS/TSX） |

## 達成状況

| ID | ストーリー | SP | 状態 | 備考 |
| :--- | :--- | :--: | :--- | :--- |
| US26 | システムにログインする | 3 | ✅ 完了 | 資格情報照合・ロール別ダッシュボード・不一致/ロック/無効化・失敗5回ロック・未認証誘導・ログ |
| US27 | システムからログアウトする | 1 | ✅ 完了 | セッション破棄・ログイン画面復帰・ログ（ブラウザバック抑止は IT2 で確認） |
| US02 | 荷主を登録する | 2 | ✅ 完了 | 個人登録・住所/連絡先入力・荷主 ID 発行表示・Email 重複時に既存 ID 提示 |
| US03 | 法人荷主を登録する | 2 | ✅ 完了 | 契約番号・割引率 0〜30% 境界・htmx 法人フィールド差替 |

## 技術的成果（後続イテレーションの基盤）

- **ウォーキングスケルトン**: NestJS モジュラーモノリス（`contexts/<bc>/{domain,application,infrastructure,presentation}` + `shared`）、TSX SSR（renderToStaticMarkup）+ htmx、Kysely（CamelCasePlugin）
- **DB 基盤**: node-pg-migrate 初期マイグレーション（users/user_roles/shipper）、pg-mem（ローカル）⇔ 実 PostgreSQL（DATABASE_URL）切替、冪等シード
- **認証基盤（Security）**: セッションベース認証・RBAC 6 ロール・`AuthenticatedGuard`/`RolesGuard`・ログイン失敗ロック
- **品質ゲート**: Vitest（swc デコレータ対応）・ESLint・Prettier・dependency-cruiser（ヘキサゴナル依存方向・BC 独立性）・Playwright・GitHub Actions CI・SonarQube 連携
- **実行基盤**: SWC（dev/serve/e2e）+ tsc（build）の二本立て（ADR-006）、ライブリロード

## 品質指標

| メトリクス | 実績 | 目標 | 判定 |
| :--- | :--- | :--- | :--- |
| 単体・統合テスト | 132 件 green | 全 green | ✅ |
| E2E（Playwright） | 5 件 green | 主要シナリオ | ✅ |
| カバレッジ（全体） | 94.5% | 80% | ✅ |
| SonarQube Bug / Vulnerability | 0 / 0 | 0 / 0 | ✅ |
| Code Smell / 重複率 | 0 / 0.0% | 0 / <3% | ✅ |
| SonarQube Quality Gate | PASS | PASS | ✅ |
| CI（verify + E2E） | success | success | ✅ |

## レビュー結果

XP 5 視点（programmer / tester / architect / technical-writer / user-representative）のマルチパースペクティブレビューを実施（[レビューレポート](../review/IT1実装_review_20260728.md)）。高優先度 5 件（H1〜H5）と収束した中優先度 4 件（M1〜M4）を本イテレーション内で対応した。

- H1: 住所・連絡先を永続化まで貫通（Address 幽霊フィールド解消）
- H2: 登録完了時に荷主 ID を表示
- H3: Email 重複時に既存荷主 ID を提示
- H4: 未使用 passport 依存削除 + ADR-006 起票
- H5: README 作成・手順書の不整合修正

## ADR

- **ADR-006**: 認証はセッションベースの自作ガードとし Passport を採用しない（実行は SWC/tsc）

設計反映: `data-model.md`（users ロックカラム）、`ui_design.md`（荷主登録画面）を実装と同期。

## 課題と残作業（IT2 引き継ぎ）

- Email 重複時の「既存荷主を選択して使う」導線（今回は ID 提示まで）
- ログアウト後のブラウザバック抑止（Cache-Control: no-store）
- dependency-cruiser への `shared → contexts` 逆流禁止ルール追加
- ダッシュボードのロール別「作業入口」カード
- domain-model の他 take 由来「実装状況」注記のクリーンアップ

詳細は [ふりかえり](retrospective-1.md) の Try を参照。

## 次イテレーション（IT2）

**スコープ**: US01 見積作成・US04/US05 貨物予約登録・US06 経路設計者への引き渡し（15SP、序盤アウトサイドイン継続）。Booking Context / Estimation Context の実装に集中する。IT1 で整備した基盤（NestJS/TSX/DB/認証/RBAC/CI/SonarQube）を再利用する。
