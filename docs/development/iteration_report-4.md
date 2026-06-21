---
title: イテレーション 4 完了報告書
date: 2026-06-21
---

# イテレーション 4 完了報告書

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT4 |
| 期間 | 2026-08-03 〜 2026-08-16（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | US09 経路選択・確定、US11 経路情報を予約に紐付ける、US12 経路通知、US13 予約確定の 4 ストーリー（計 11 SP）を完成させ、IT3 マルチパースペクティブレビュー高優先度 8 件を解消する |
| 計画 SP | 11（US09: 3 + US11: 2 + US12: 3 + US13: 3） |
| 実績 SP | 11 |
| 達成率 | 100% |

## ストーリー実績

| ID | ストーリー | 状態 | 計画 SP | 実績 SP |
|----|-----------|------|---------|---------|
| US09 | 経路を選択・確定する | ✅ 完了 | 3 | 3 |
| US11 | 経路情報を予約に紐付ける | ✅ 完了 | 2 | 2 |
| US12 | 確定経路を荷主に通知 | ✅ 完了 | 3 | 3 |
| US13 | 予約確定（確定 / 再設計 / キャンセル） | ✅ 完了 | 3 | 3 |
| **合計** | | | **11** | **11** |

## タスク実績

29 タスクすべて完了（IT3 申し送り 0.x: 9 件、US09: 1.1-1.5: 5 件、US11: 2.1-2.5: 5 件、US12: 3.1-3.5: 5 件、US13: 4.1-4.5: 5 件）。

### IT3 申し送り（0.x）

| # | タスク | 完了内容 |
|---|--------|---------|
| 0.1 | 表示フォーマッタ層 | `views.helpers.DisplayFormatters` を新設し Money / Instant / LocalDate / Location を統一フォーマット化、3 画面を移行 |
| 0.2 | Voyage 空文字許容オーバーロード削除 | `Voyage.register(2 引数)` / `reconstruct(3 引数)` 廃止、V8 の `DEFAULT ''` 撤去 |
| 0.3 | InMemoryVoyageRepository フィルタ実装 | `findByCriteria` を ScalikeJDBC 版と同等の AND 結合フィルタに置換、契約テスト 5 件を `support/` に集約 |
| 0.4 | Endpoint ハッピーパス追加 | seed なし 200 + 条件緩和ガイド表示の回帰テスト |
| 0.5 | ADR 0007 起案 | 楽観ロック `Either[DomainError.ConcurrentModification, A]` API（実装は IT5+） |
| 0.6 | queryservices 命名拡張 + ADR 0008 | ArchUnit ルール 4 を `*QueryService` / `*Query` / `*Command` / `*Result` / `*Candidate` 許容に拡張、Routing DTO を queryservices/ に集約 |
| 0.7 | Estimate.findAll N+1 解消 | estimate + route_candidate を 2 クエリで一括取得、Map[Long, List] で関連付け |
| 0.8 | iteration_plan-3 修正 | VARCHAR 桁数表記と重複 ADR 表 |
| 0.9 | 設計ドキュメント整合化 | domain-model / data-model / ui_design に IT4 反映（BookingStatus 9 値・新規 2 テーブル・URL 統一・ボタン表追記） |

### US09 経路選択・確定

| # | タスク | 完了内容 |
|---|--------|---------|
| 1.1 | RouteCandidateSelection 集約 + ADR 0009 | Routing Context に新集約、`RouteSelectionStatus` enum、命名規約を ADR 化 |
| 1.2 | Flyway V9 | `route_candidate_selection`（booking_id UNIQUE、楽観ロック付き） |
| 1.3 | SelectRouteCommand + confirmRoute | for 内包でパース・状態遷移を集約 |
| 1.4 | 経路候補画面に確定ボタン | POST `/bookings/:id/routes/:idx/confirm`（PRG）、CSRF 保護 |
| 1.5 | 統合 + E2E | 直行確定 / 404 / US10 リンク表示 |

### US11 経路情報紐付け

| # | タスク | 完了内容 |
|---|--------|---------|
| 2.1 | Itinerary VO + Cargo.assignItinerary | RouteProposed → RouteAssigned、itinerary を集約に保持 |
| 2.2 | BookingStatus.RouteAssigned 追加 | 9 値化、canTransitionTo に正常 / 再設計 / キャンセルパスを追加 |
| 2.3 | 同一リクエストで紐付け | Controller 層 ACL で confirmRoute → assignItinerary を順次実行、Flyway V10 で `cargo.itinerary_voyages` カラム追加 |
| 2.4 | 営業ダッシュボードに RouteAssigned 一覧 | DashboardComposer 拡張、経路通知ボタン + 通知ログリンク |
| 2.5 | assignItinerary テスト | 成功 / 状態違反 / 再紐付け禁止 / 不存在 / 空リスト |

### US12 経路通知

| # | タスク | 完了内容 |
|---|--------|---------|
| 3.1 | NotificationLog 集約 | Booking Context 内、`NotificationType` enum（3 値）、payload 必須 |
| 3.2 | Flyway V11 | `notification_log`（CHECK 制約、複合 INDEX `(booking_id, sent_at DESC)`） |
| 3.3 | NotifyRouteCommandService | RouteAssigned 必須、payload JSON 風生成、Clock 注入 |
| 3.4 | 通知ボタン + 通知ログ画面 | dashboard のフォーム、`/notifications` 画面、PRG |
| 3.5 | DB 往復テスト | 永続化往復 + sort 順 + 他予約混入なし |

### US13 予約確定

| # | タスク | 完了内容 |
|---|--------|---------|
| 4.1 | BookingStatus 遷移マトリクス | RouteAssigned → {Confirmed / Cancelled / RouteProposed} のテスト追加 |
| 4.2 | 3 コマンド追加 | Cargo.confirm / reproposeRoute / cancel + BookingCommandService 同名メソッド（private `transition` ヘルパで共通化） |
| 4.3 | 予約詳細にボタン | 状態に応じて「予約を確定」「経路再設計に戻す」「キャンセル」（確認ダイアログ付き）を表示 |
| 4.4 | 操作後の通知記録 | confirm 成功 → BookingConfirmed、cancel 成功 → BookingCancelled |
| 4.5 | 統合 + E2E | 確定 / 再設計 / キャンセル / 二重キャンセル拒否 |

## 品質メトリクス

| 指標 | 計測値 | 目標 | 判定 |
|------|--------|------|------|
| テスト総数 | 288 件 | – | – |
| テスト成功率 | 100%（288/288） | 100% | ✅ |
| Statement カバレッジ | 88.21% | 80% | ✅ |
| Branch カバレッジ | 79.06% | 70% | ✅ |
| ArchUnit ルール | 5/5 緑 | 5/5 | ✅ |
| マイグレーション | V1-V11 適用済 | – | ✅ |
| scalafmt / scalafix | ✅ | – | ✅ |

## ADR 実績

| ADR | タイトル | ステータス |
|-----|---------|-----------|
| 0007 | 楽観ロックを `Either[DomainError.ConcurrentModification, A]` API として表現 | 提案（実装 IT5+） |
| 0008 | queryservices パッケージ命名規約を入出力 DTO 許容に拡張 | 承認 |
| 0009 | 経路選択を独立集約 `RouteCandidateSelection` として永続化 | 提案 |

## マイグレーション実績

| バージョン | 内容 |
|----|------|
| V9 | route_candidate_selection（US09） |
| V10 | cargo.itinerary_voyages カラム追加（US11） |
| V11 | notification_log（US12 / US13） |

## 主要な設計判断

| 論点 | 判断 | 理由 |
|------|------|------|
| RouteCandidateSelection の所属 Context | Routing Context | 探索結果の延長として閉じ込め、Booking 側は ACL で受ける |
| Notification の独立 Context 化 | 見送り（Booking Context 内集約） | コンテキスト爆発回避、IT5+ で再評価 |
| 経路選択 + 予約紐付けの統合 | Controller 層で順次実行 | ArchUnit ルール 3（コンテキスト間 application 依存禁止）と ACL の両立 |
| `Cargo.itinerary` の永続化 | V10 で itinerary_voyages カラム追加 | 状態 RouteAssigned とのリロード整合性を担保 |

## マルチパースペクティブレビュー所見（IT5 申し送り候補）

XP プログラマー / テスター視点の self-review で抽出された高優先度 6 件：

| # | 観点 | 指摘 | 修正方針 |
|---|------|------|---------|
| H1 | プログラマー | 通知 JSON ペイロードのハードコーディング集中（`BookingCommandService:115/130`、`NotifyRouteCommandService:47`）。文字列補間でエスケープ漏れリスク | `NotificationPayload` VO を新設、Play JSON または型安全 ADT で構築、`logNotification` / `buildPayload` を一本化 |
| H2 | プログラマー | `BookingCommandService` の `transition` ヘルパーが confirm/repropose/cancel のみ適用、`assignToRouting` / `assignItinerary` で重複 | 全 lifecycle メソッドを `transition` 経由に統一、`CargoErrorMessages` 抽出 |
| H3 | プログラマー | `RoutingCommandService.parseVoyages` の `foldLeft + :+` で O(n²)、`persistConfirmed` の Some/None 分岐重複 | `traverse` 相当 / `foldRight` 線形化、`persistConfirmed` を `getOrElse → confirm → save` 一直線に |
| H4 | テスター | Routing で confirm した voyages と Booking に assign する voyages の **一致を保証するテスト欠落**（US11 受入基準のトレース漏れ） | E2E に「voyages 不一致時に拒否 / 一致のみ受理」、または ACL 単体テスト追加 |
| H5 | テスター | 通知 payload の検証が `include("VY-1")` 部分文字列マッチで脆い（JSON 構造破壊でも合格） | `circe` 等で JSON パース → フィールド構造アサート、空 / 1 / 複数航海のプロパティベーステスト |
| H6 | テスター | `cancel` テストのタイトルが「4 状態から可能」だが実行は Preliminary 1 ケースのみ。`notify` のべき等性未検証 | デシジョンテーブル（5 状態 × 5 操作 = 25 セル）で全網羅、`notify` のべき等性仕様を確定 |

詳細は [it4_self_review_20260621.md](../review/it4_self_review_20260621.md) を参照。

## IT5 への申し送り

### レビュー指摘高優先度（上記 H1-H6）

### 残機能 / 設計

- **US10 経路条件再調整**: 経路候補画面のゼロ件ガイドから予約詳細へのリンクは設置済。実機能は IT9 予備
- **MailHog 経由のメール送信**: 現状 DB ログのみ。NotificationLog → 実メール送信のアダプター追加
- **ADR 0007 楽観ロック Either API 化**: 段階移行を IT5 で着手（CargoRepository.save から）
- **VoyageCommandService の RegisterVoyageCommand フォーム拡張**: vesselName / carrierCode / supportedCargoTypes の入力 UI 化（現状デフォルト空）
- **NotifyRouteCommandService に料金概算ペイロード**: PricingService 連携で payload に金額を含める
- **`docs/design/ui_design.md` の予約詳細ボタン表に「経路再設計に戻す」「予約をキャンセル」を追記**: タスク 4.3 実装と整合化
- **notification_log payload の検索性**: 現状 TEXT に JSON 直書き。将来の検索要件発生時に jsonb 化 ADR 追補を検討

## 参考リンク

- 計画書: [iteration_plan-4.md](iteration_plan-4.md)
- リリース計画: [release_plan.md](release_plan.md)
- ADR: [0007](../adr/0007-optimistic-lock-either-api.md) / [0008](../adr/0008-queryservices-package-naming.md) / [0009](../adr/0009-route-candidate-selection-aggregate.md)
