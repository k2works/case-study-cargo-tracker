---
title: IT4 self-review（マルチパースペクティブ）
date: 2026-06-21
---

# IT4 self-review（マルチパースペクティブ）

XP プログラマー / テスター視点の 2 エージェントによる self-review。
正式なマルチパースペクティブレビュー（XP 5 エージェント並列）は IT5 staging 完了後に `developing-review` で実施予定。

## サマリ

| 観点 | 高 | 中 | 低 |
|------|---|---|---|
| プログラマー | 3 | – | – |
| テスター | 3 | – | – |
| 合計 | **6** | – | – |

## プログラマー観点

### H1. 通知 JSON ペイロードのハードコーディング集中

- **箇所**: `BookingCommandService.scala:115`, `:130`, `NotifyRouteCommandService.scala:47-48`
- **問題**: `s"""{"bookingId":"...","status":"..."}"""` を 3 箇所で手書き。エスケープ漏れ・フィールド名タイポの危険、`NotificationType` ごとに DRY 違反
- **修正方針**: `NotificationPayload` 値オブジェクトを `valueobjects/` に新設し、Play JSON もしくは型安全 ADT で構築。`BookingCommandService.logNotification` と `NotifyRouteCommandService.buildPayload` を一本化。テストは payload 構造の契約として書き直す

### H2. `BookingCommandService` の責務肥大とエラーマッピング重複

- **箇所**: `BookingCommandService.scala:100-103`, `:152-156`, `:174-178`
- **問題**: `transition` ヘルパーを途中導入したが、`assignToRouting` と `assignItinerary` が未適用で重複が残る。1 クラス 200 行超で SRP 違反気味
- **修正方針**: (a) `assignToRouting` / `assignItinerary` も `transition` 経由へ統一、(b) `Cargo.Error` → メッセージ変換を `CargoErrorMessages` に抽出、(c) 中期的には `LifecycleCommandService`（confirm / repropose / cancel）を別クラスへ分離検討

### H3. `RoutingCommandService.parseVoyages` の O(n²) と TDD 機会逸失

- **箇所**: `RoutingCommandService.scala:27-32`
- **問題**: 末尾 `:+` で List 全走査、`traverse` 相当の標準パターンを再発明。`persistConfirmed` の Some/None 分岐内でも `save → 返す` が二重化
- **修正方針**: `raw.traverse(VoyageNumber(_).left.map(...))` か `raw.foldRight` で線形化。`persistConfirmed` は `selection = existing.getOrElse(create(...))` → `confirm` → `save` の一直線にまとめる（25 行 → 10 行程度）

## テスター観点

### H4. 経路紐付けの「整合性」検証が欠落（受け入れ基準のトレース漏れ）

- **箇所**: `BookingCommandServiceSpec.scala:340-355`、`RoutingCommandServiceSpec.scala:18-23`
- **問題**: `confirmRoute` と `assignItinerary` を別々に検証しているが、両者の voyages が一致するかを保証するテストがない。US11 の本質「確定経路を予約に紐付ける」が任意 voyages で通る
- **修正方針**: E2E に「不一致時に拒否 / 一致のみ受理」シナリオを追加、または application 層に `RouteCandidateSelectionRepository` を注入し集約間整合性を検証するユニットテスト

### H5. 通知 payload 検証が偽 stub に近い（脆い検証）

- **箇所**: `NotifyRouteCommandServiceSpec.scala:73-76`, `NotificationLogSpec.scala:22`, `BookingCommandServiceSpec.scala:493`
- **問題**: payload を `should include("VY-1")` で部分文字列マッチ。JSON 構造が壊れても通る
- **修正方針**: `circe` 等で JSON パース → `voyages: List[String]` / `origin` / `destination` を構造的にアサート。空 / 1 / 複数 / 特殊文字 UnLocode のプロパティベーステスト

### H6. 状態遷移とべき等性の境界が部分網羅

- **箇所**: `BookingCommandServiceSpec.scala:469-479`, `NotifyRouteCommandServiceSpec.scala`
- **問題**: cancel テストのタイトル「4 状態から可能」だが実行は Preliminary 1 ケースのみ（タイトル詐欺）。`notify` のべき等性（2 回呼ぶと 2 ログ vs 重複抑止）未確定
- **修正方針**: デシジョンテーブル（現在状態 5 × 操作 5 = 25 セル）で全網羅。`cancel` は 4 状態それぞれを `forAll` でパラメタライズ。`notify` は仕様を顧客と決めてテストで固定

## 参考観察（中以下）

- `notification_log` の payload を `String` カラムに JSON 直書きしている点。マイグレーション V11 と整合は取れているが将来の検索要件で詰む可能性。jsonb 化 ADR 追補候補

## 次アクション

- すべて IT5 申し送り（`iteration_report-4.md` の「IT5 への申し送り」に統合）
- IT5 staging 完了後に正式な `developing-review`（XP 5 エージェント並列）を実施
