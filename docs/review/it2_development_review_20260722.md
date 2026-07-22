---
title: IT2 開発成果物レビュー - 航海スケジュール
description: IT2（US24/US25/US07）実装のマルチパースペクティブレビュー（xp-programmer/tester/architect/technical-writer/user-representative）
published: true
date: 2026-07-22T00:00:00.000Z
tags: review, development, it2, routing, rust
---

# IT2 開発成果物レビュー - 航海スケジュール

## レビュー対象

- domain-routing（Voyage 集約・値オブジェクト・ポート）
- app-routing（登録/更新/検索ユースケース）
- infra-persistence（SqlxVoyageRepository・migration）
- interface-web（航路管理画面・RoleGuard 認可 extractor）
- テスト（ドメイン 16 + app 5 + Repository 統合 4 + HTTP フロー 6 + US03/US05 の 4）

## 総合評価

IT2 の実装はヘキサゴナル + DDD の層分離、newtype による不変条件の型表現、TDD サイクルを高水準で満たし、既存 shipper/booking パターンと一貫した品質である。特に `RoleGuard<R>` 認可 extractor は SOLID の OCP を体現し、認可の書き忘れを型で防ぐ優れた設計。一方、**(1) 受入基準に対するテスト網羅の漏れ（US24 寄港地複数・日付逆転 HTTP 経路、US07 0 件時、US25 キャンセル不変）、(2) `search` の三重 N+1、(3) interface 層の DIP 逸脱（ADR-0001 違反）、(4) ui_design.md への画面反映漏れ**が共通指摘として挙がった。DIP 逸脱と一部の受入基準未達（寄港地複数・差分確認・出発日検索）は既知の繰り越し事項だが、条件付き差し戻しに相当する。

## 改善提案（重要度順）

### 高（IT2 内または IT2 後半で対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | US24「寄港地を複数かつ順序付き」の HTTP フローテスト追加（複数区間登録・一覧表示・区間跨ぎ逆転 422） | voyage_flow_test.rs | tester, user-rep | 受入基準のコア。ドメイン単体は緑だがフォームパース→複数区間の HTTP 経路が未実証 |
| 2 | US07「0 件時の再検索メッセージ」の HTTP フローテスト追加 | voyage_flow_test.rs | tester, user-rep | 0 件は 1件/複数件と別の同値クラス。`voyage-empty` 表示が受入基準 |
| 3 | US25「キャンセル時に既存不変」のテスト追加＋差分確認画面の実装見直し | voyage_flow_test.rs, voyage_edit.html | tester, user-rep | 現状は現在値カード表示のみで差分ハイライトなし。キャンセル導線の回帰を捕捉できていない |
| 4 | US24 日付逆転入力の HTTP エラー経路テスト（422 + エラー種別識別） | voyage_flow_test.rs | tester | 異常系の受入基準は利用者が直接踏む導線。`voyage-error` の文字列アサートが緩く種別を区別しない |
| 5 | interface 層の DIP 逸脱を ADR で意図的負債として明文化し、IT2 後半で composition root へ集約 | interface-web/lib.rs:757,824,839,859,886 | architect, programmer | ADR-0001「interface 層は sqlx 実装を直接参照しない」に構造違反。Task #7 で対応予定 |
| 6 | ui_design.md に航路登録画面（/voyages/new）・更新画面（/voyages/{n}/edit）を反映 | docs/design/ui_design.md | technical-writer | 実装が先行し設計が取り残されている（data-model.md は反映済み） |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 7 | `search` の三重 N+1（全件ロード後フィルタ＋voyage ごと子テーブル個別クエリ）を SQL WHERE 絞り込みへ。当面は ADR/コメントで暫定を明記 | voyage_repository.rs:179 | programmer, architect | 件数増で線形劣化。US07 は本来 DB で絞るべき |
| 8 | CargoType の文字列展開の DRY 化（voyage_list・build_voyage_input・form_to_data・cargo_types_label に散在） | interface-web/lib.rs:620,686,700,758 | programmer | ケース追加時に 3〜4 箇所修正。enum で直接 match すれば網羅性を型が強制 |
| 9 | CQRS の非対称性（Voyage は集約経由クエリ・Read Model 未分離）を ADR で判断基準として固定 | app-routing, voyage_repository.rs | architect | 他コンテキストとの一貫性のため設計判断を明文化 |
| 10 | 必須項目未入力時の「未入力箇所を明示したエラー」テスト＋メッセージ粒度 | voyage_flow_test.rs, interface-web/lib.rs:740 | tester, technical-writer | `Domain`/`Location` を 1 メッセージに丸めており粒度を捨てている |
| 11 | VesselName/Carrier の上限境界値テスト（100/101 文字）追加 | value_objects.rs:283 | tester | VoyageNumber は 21 文字境界を突いているのに非対称。ミューテーション耐性 |
| 12 | flash メッセージの percent-encoded 即値をキー化しテンプレート側で文言解決 | interface-web/lib.rs:826,888 | programmer | マジック文字列で可読性ゼロ・変更時に手作業エンコード |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 13 | leg2 存在判定に `leg2_arr_t` が欠落（到着日時のみ入力の leg2 が無視される非対称） | interface-web/lib.rs:722 | programmer | 意図的でなければ 4 項目すべて対象に |
| 14 | Schedule 区間隣接の等号境界（乗り継ぎ 0 分＝有効）テスト追加 | value_objects.rs:328 | tester | `>` の境界の OK 側を突く |
| 15 | app-routing の `&InMemoryVoyageRepository` 二重実装を Arc 共有に | app-routing/lib.rs:383 | programmer, tester | テストが書きにくい＝設計シグナル。共有シナリオ前に検討 |
| 16 | voyage_update の find 2 回実行（成功パスで無駄） | interface-web/lib.rs:853, app-routing:141 | programmer | エラー時のみ current_row が必要 |
| 17 | Schedule の添字アクセスが panic 前提（不変条件はコンストラクタ経由のみ保証） | value_objects.rs:236 | programmer | NonEmptyVec 未使用の判断ならコメントを |

## 矛盾事項

なし（各視点の指摘は補完的で相反しない）。

## 高優先度指摘への対応方針

| # | 指摘 | 対応方針 |
|---|------|----------|
| 1-4 | 受入基準のテスト漏れ（寄港地複数・0件・キャンセル・日付逆転） | **IT2 内で対応**。voyage_flow_test に 4 本追加し受入基準を HTTP で実証。差分確認画面は US25 の差分ハイライト実装を検討 |
| 5 | DIP 逸脱 | **Task #7（IT2 後半）で composition root 集約＋ADR 起票**。本レビューで意図的負債として記録 |
| 6 | ui_design.md 反映 | **IT2 クローズ時に反映**。航路登録・更新画面のワイヤーフレーム・画面遷移を追記し「閲覧専用」記述を修正 |

## エージェント別サマリー

- **xp-programmer**: 層分離・newtype・RoleGuard を高評価。search の N+1、CargoType 文字列展開の DRY、flash マジック文字列、leg2 判定の非対称を指摘。
- **xp-tester**: ピラミッドバランス良好。US24 寄港地複数・日付逆転 HTTP、US07 0 件、US25 キャンセルの受入基準テスト漏れを最優先指摘。境界値の非対称（VesselName 上限）。
- **xp-architect**: 依存方向・BC 独立・集約凝集は優秀。ADR-0001 違反（interface→sqlx 直接参照）、三重 N+1、CQRS 非対称性の ADR 化を推奨。
- **xp-technical-writer**: rustdoc は高水準。ui_design.md への画面反映漏れが最重要。エラーメッセージ粒度の欠落。
- **xp-user-representative**: 寄港地複数入力・更新差分確認・出発期間検索の 3 点が受入基準未達で条件付き差し戻し相当。

## 更新履歴

| 日付 | 更新内容 |
|------|---------|
| 2026-07-22 | 初版作成（5 エージェント並列レビュー統合） |
