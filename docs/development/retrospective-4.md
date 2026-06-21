---
title: イテレーション 4 ふりかえり
date: 2026-06-21
---

# イテレーション 4 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| 期間 | 2026-08-03 〜 2026-08-16（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | US09 経路選択・確定、US11 経路紐付け、US12 経路通知、US13 予約確定の 4 ストーリー（11 SP）を完成させ、IT3 マルチパースペクティブレビュー高優先度 8 件を解消する |
| 計画 SP | 11（US09: 3 + US11: 2 + US12: 3 + US13: 3） |
| 実績 SP | 11（100%） |

## 達成事項

- **US09（3 SP）**: ADR 0009 起案、Flyway V9（route_candidate_selection）、`RouteCandidateSelection` 集約 + `RouteSelectionStatus` enum、`RoutingCommandService.confirmRoute` で Pending → Confirmed 状態管理、経路候補画面に「この経路で確定」ボタン + CSRF 保護 PRG、E2E（直行確定 / 404 / US10 リンク表示）
- **US11（2 SP）**: `Itinerary` 値オブジェクト、`BookingStatus.RouteAssigned` 追加（9 値）、`Cargo.assignItinerary`、Flyway V10（cargo.itinerary_voyages カラム）、`BookingCommandService.assignItinerary`、Controller 層 ACL で confirmRoute → assignItinerary を同一リクエストで順次実行、営業ダッシュボードに `RouteAssigned` 一覧 + 経路通知ボタン
- **US12（3 SP）**: `NotificationLog` 集約 + `NotificationType` enum（3 値）、Flyway V11（notification_log + CHECK + 複合 INDEX）、`NotifyRouteCommandService` + Clock 注入、通知ログ画面、E2E（正常 / 未紐付け拒否）
- **US13（3 SP）**: `Cargo.confirm` / `reproposeRoute` / `cancel` ドメインメソッド、`BookingCommandService` に対応 3 メソッド（`transition` 共通骨格）、予約詳細に状態別 3 ボタン + 確認ダイアログ、操作後の通知ログ自動記録（BookingConfirmed / BookingCancelled）、E2E（4 シナリオ）
- **IT3 申し送り 9 件**: 表示フォーマッタ層（Money/Instant/Location 統一）、Voyage オーバーロード削除 + V8 DEFAULT '' 撤去、`InMemoryVoyageRepository` フィルタ実装（契約テスト）、Endpoint ハッピーパス、ADR 0007（楽観ロック Either API）、queryservices 命名拡張 + ADR 0008、`Estimate.findAll` N+1 解消、iteration_plan-3 修正、設計ドキュメント整合化（domain-model / data-model / ui_design への IT4 反映）

### 品質メトリクス

| 指標 | 結果 |
|------|------|
| テスト件数 | 288 件 / 全件成功（55 Suites） |
| Statement coverage | 88.21%（scoverage） |
| Branch coverage | 79.06% |
| ArchUnit | 5/5 緑 |
| マイグレーション | V1-V11 適用済 |
| scalafmt / scalafix / CI | ✅ |

## KPT

### Keep（継続したいこと）

- **状態遷移を集約に閉じ込めるパターン**: `Cargo.{assignToRouting, assignItinerary, confirm, reproposeRoute, cancel}` のように状態変更を集約メソッドにし、`canTransitionTo` で防衛するスタイルを継続。IT4 で 5 メソッドに育っても可読性が崩れていない
- **ADR 駆動の設計判断**: 0007 / 0008 / 0009 を起案し、命名規約変更（queryservices 拡張）や新集約導入（RouteCandidateSelection）の理由が永続化された
- **Controller 層を ACL とする境界戦術**: ArchUnit ルール 3（コンテキスト間 application 依存禁止）を破らずに、US09 確定 → US11 紐付けの同一リクエスト実行を実現
- **`transition` 共通骨格による DRY**: confirm / reproposeRoute / cancel の 3 メソッドを 1 ヘルパに集約
- **IT3 マルチパースペクティブレビュー 8 件のうち 7 件を 0.x で解消**: 計画段階で残課題を見える化して着手したことで申し送りの「腐敗」を防止

### Problem（問題だったこと）

- **`transition` ヘルパの適用漏れ**: US13 タスク 4.2 で導入したが、既存の `assignToRouting` / `assignItinerary` に遡及適用しなかった（self-review H2）
- **通知 JSON ペイロードを文字列補間で 3 箇所手書き**: `BookingCommandService` + `NotifyRouteCommandService` で型安全性なし。エスケープ漏れ・キータイポを静的に検出できない（self-review H1）
- **テスト assertion の脆弱性**: `payload should include("VY-1")` 等の部分文字列マッチが複数。JSON 構造が壊れても緑になる「擬陽性」テスト（self-review H5）
- **コンテキスト間の整合性テスト欠落**: `confirmRoute` の voyages と `assignItinerary` の voyages が一致するかを保証するテストがない（self-review H4）
- **デシジョンテーブル網羅漏れ**: `cancel` のタイトルが「4 状態から可能」だが実行は 1 状態のみ（H6）。`notify` のべき等性仕様も未確定
- **計画 V10 / V11 の番号繰り下げ**: 計画書では V10=notification_log だったが、US11 で cargo.itinerary_voyages 用に V10 を充てたため通知用は V11 に。計画書は事後修正

### Try（次イテレーションで試したいこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|---------|
| T1 | `NotificationPayload` 値オブジェクト + Play JSON で型安全化 | AI Agent | IT5 早期 | self-review H1 解消、テスト H5 とセットで構造アサート可能化 |
| T2 | 全 lifecycle メソッドを `transition` ヘルパ経由に統一 + `CargoErrorMessages` 抽出 | AI Agent | IT5 | self-review H2 解消、SRP 改善 |
| T3 | `RoutingCommandService.parseVoyages` を `traverse` 相当に書き換え + `persistConfirmed` 一直線化 | AI Agent | IT5 | self-review H3 解消（O(n²) → O(n)） |
| T4 | confirm voyages と assign itinerary voyages の整合性 E2E / ACL テスト追加 | AI Agent | IT5 | self-review H4 解消、US11 受入基準のトレース完成 |
| T5 | 状態 × 操作のデシジョンテーブルを `forAll` でパラメタライズ化 | AI Agent | IT5 | self-review H6 解消、状態遷移の網羅検証 |
| T6 | ADR 0007 楽観ロック Either API を CargoRepository から段階移行 | AI Agent | IT5 | 例外貫通の根絶 |
| T7 | 計画書のマイグレーション番号は実装中に都度更新する運用に変更 | AI Agent | 計画策定時 | 番号繰り下げの事後修正を防ぐ |

## ベロシティ分析

| イテレーション | 計画 SP | 実績 SP | 達成率 | 累積ベロシティ |
|--------------|---------|---------|--------|--------------|
| IT1 | 12 | 12 | 100% | 12 |
| IT2 | 12 | 12 | 100% | 12 |
| IT3 | 11 | 11 | 100% | 11.67（直近 3 IT 平均）|
| IT4 | 11 | 11 | 100% | 11.5（直近 3 IT 平均）|
| **累積** | **46** | **46** | **100%** | — |

4 イテレーション連続 100% 達成。複数ストーリーの統合（US09 → US11 同一リクエスト実行、US13 操作後の自動通知ログ）が増えてもベロシティが維持できた。

## 次のステップ

1. IT4 自己レビュー高優先度 6 件（H1-H6）を IT5 申し送りタスクに変換
2. IT5 計画策定（残ストーリー: US14 追跡番号発行 / Tracking Context 立ち上げ等を予定）
3. GitHub Project（#30）に IT4 完了分を `/syncing-github-project --sync` で反映
4. IT5 staging 完了後に正式な `developing-review`（XP 5 エージェント並列）

## 関連ドキュメント

- [IT4 計画](./iteration_plan-4.md)
- [IT4 完了報告書](./iteration_report-4.md)
- [IT4 self-review](../review/it4_self_review_20260621.md)
- [IT3 ふりかえり](./retrospective-3.md)
- [リリース計画](./release_plan.md)
