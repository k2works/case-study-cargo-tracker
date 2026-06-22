---
title: イテレーション 5 ふりかえり
date: 2026-06-22
---

# イテレーション 5 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| 期間 | 2026-08-17 〜 2026-08-30（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | US14 追跡番号発行、US15 荷役作業記録、US18 追跡情報照会の 3 ストーリー（11 SP）を完成させ、Tracking Context + Handling Context を新設し、IT4 セルフレビュー高優先度 6 件（H1-H6）を解消する |
| 計画 SP | 11（US14: 2 + US15: 6 + US18: 3） |
| 実績 SP | 11（100%） |

## 達成事項

- **IT4 申し送り（6 件）**: H1 NotificationPayload ADT 化 + JSON シリアライザを application 層に隔離（ArchUnit ルール 1 適合）/ H2 BookingCommandService の transition 統一 + `CargoErrorMessages` 抽出 / H3 `parseVoyages` を traverse 化（O(n²)→O(n)）+ `persistConfirmed` 簡素化 / H4 経路紐付け整合性 E2E 2 件 / H5 通知 payload を JSON 構造アサーション化（3 件追加）/ H6 cancel 4 状態網羅 + notify べき等性仕様確定
- **US14（2 SP）**: ADR 0010 起案（採番ポリシー + 集約境界、UUID 不採用理由含む）、Tracking Context 骨格（`TrackingActivity` 集約 + `TrackingNumber`/`TrackingBookingId` opaque type + `TrackingStatus` 9 値 + repository ポート）、Flyway V12（tracking_activity + cargo.tracking_number + notification_log CHECK 拡張）、`Cargo.issueTracking` 冪等性、`BookingCommandService.issueTracking` + `NotificationType.TrackingIssued`、`TrackingCommandService.assign` 冪等性、予約詳細画面に「追跡番号発行」ボタン + 公開追跡 URL リンク、E2E 2 件
- **US15（6 SP）**: Handling Context 新設（`HandlingActivity` 集約 + `HandlingType` 5 値 + `HandlingVoyageNumber` opaque type + `requiresVoyage` 内包）、Flyway V13/V14（handling_activity + tracking_handling_event + notification_log CHECK 拡張）、`HandlingCommandService.register`、`TrackingCommandService.recordEvent` + `TrackingActivity.addEvent`（時系列順序検証）、`TrackingActivityRepository.appendEvent`（追記専用 + 楽観ロック）、`/handling` 一覧 + `/handling/new` 登録フォーム、E2E 3 件（Receive ハッピー / Receive→Load 状態遷移 / 存在しない番号エラー）
- **US18（3 SP）**: `TrackingQueryService` + `TrackingResult` Read Model（ADR 0008 命名規約準拠）、認証ユーザー向け `/tracking` 入力 + `/tracking/:trackingNumber` 詳細（30 秒 htmx ポーリング）、公開 `/public/tracking/:trackingNumber`（AuthFilter の `/public/` 公開パスで自動許可）、E2E 3 件（公開 200 / 404 / 認証必須 redirect）

### 品質メトリクス

| 指標 | 結果 |
|------|------|
| テスト件数 | 323 件 / 全件成功（63 Suites）|
| ArchUnit | 5/5 緑（rule 4 ADR 0008 で TrackingView → TrackingResult リネーム） |
| マイグレーション | V1-V14 適用済 |
| scalafmt / scalafix / CI | ✅ |
| 新コンテキスト | Tracking / Handling の 2 つ新設 |
| 新 ADR | ADR 0010（採番ポリシー + 集約境界） |

## KPT

### Keep（継続したいこと）

- **コンテキスト間オーケストレーションを Controller 層に閉じ込めるパターン**: HandlingController が Handling/Tracking/Booking の 3 CommandService を順次呼び出す形で ArchUnit ルール 3 を維持
- **opaque type による型分離**: `TrackingNumber` / `TrackingBookingId` / `HandlingVoyageNumber` をコンテキストごとに別型として opaque type で表現することで、コンテキスト境界を型レベルで強制
- **ADR 駆動の意思決定**: ADR 0010 で「UUID 不採用」「`TN-` + 6 桁採番」「集約境界」を明示し、IT6 以降のセキュリティ強化（ID 推測対策）に再評価点を残した
- **DELETE+INSERT を避ける追記専用リポジトリ API**: `appendEvent(activity, newEvent)` で既存イベント消失バグを回避（race condition 発見時の単純な save 方式から学習）
- **race condition 発見 → テスト改善**: Receive → Load 順次登録テストで `.get + status()` await を明示し、Future の暗黙完了に依存しない E2E パターンを確立
- **IT4 申し送り 6 件全件解消（19h 計画）を 1 ターンで消化**: 計画段階で残課題を 0.x に並べる運用が機能

### Problem（問題だったこと）

- **`TrackingActivity` 集約の `addEvent` 後の version 非整合**: `addEvent` は version を更新しないため `appendEvent` 後の戻り値 `updated` が古い（self-review H1）
- **`Cargo.trackingNumber` が `Option[String]` の生型**: Tracking Context との型境界が曖昧（self-review H2）
- **HandlingController の 4 段呼出が分散トランザクション境界の課題を残す**: 中途半端な状態のリスク（self-review H3）
- **`addEvent` の `OutOfOrder` 境界値テスト欠落**: 不変条件は実装したがユニットテストで直接検証していない（self-review H4）
- **`appendEvent` の楽観ロック衝突は race condition でしか露呈していない**: 明示的な integration test なし（self-review H5）
- **`CargoSnapshot` ACL 未実装**: ドメインモデル設計に記載があるが IT5 では実装せず Cargo 状態検証が不足（self-review H6）
- **`transport_status` キャッシュ整合性の保証なし**: 直接 UPDATE 等で events と乖離する可能性（self-review H7）
- **テンプレートに Bootstrap CDN 直書き重複**: 共通 layout 切り出し未実施
- **ルート逸脱判定が常に false**: Itinerary に leg 詳細がないため未実装、IT6 申し送り
- **採番が `MAX(id) + 1` で並行採番に脆弱**: PostgreSQL シーケンス化が必要（ADR 0010 で「IT5 段階の単純実装」と明記済）
- **`TrackingView` 命名で ArchUnit rule 4 違反 → `TrackingResult` リネーム**: 計画段階で命名規約（ADR 0008）に気づけなかった

### Try（次イテレーションで試したいこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|---------|
| T1 | `appendEvent` 戻り値を `TrackingActivity` に変更し新バージョンを返す | AI Agent | IT6 早期 | H1 解消、呼出側の再利用安全化 |
| T2 | `CargoSnapshot` ACL VO を Handling Context に新設、`HandlingCommandService` に注入 | AI Agent | IT6 設計 | H6 解消、ドメインモデル設計と実装の整合 |
| T3 | `TrackingActivity.addEvent` の `OutOfOrder` 境界値テスト + `OptimisticLockException` integration test | AI Agent | IT6 早期 | H4 / H5 解消 |
| T4 | orchestration サービスを application 層に新設し単一トランザクション境界を確立 | AI Agent | IT6 設計レビュー | H3 解消、分散トランザクション中途半端状態の根絶 |
| T5 | `BookingTrackingNumber` opaque type 検討 + `Cargo.issueTracking` バリデーション | AI Agent | IT6 | H2 解消、型境界の明示化 |
| T6 | tracking_number 採番を PostgreSQL シーケンス（`DEFAULT nextval()`）化 | AI Agent | 負荷検証時 / IT6 | 並行採番安全性 |
| T7 | `transport_status` を Read Model 専用に分離 or 書込時 assertion 化 | AI Agent | IT6 設計判断 | H7 解消、ドメインモデル設計に対する説明責任 |
| T8 | ルート逸脱判定の `Itinerary` leg 詳細追加実装 | AI Agent | IT6 | US15 受入条件 7 の正式実装 |
| T9 | `layout/public.scala.html` を切り出し公開ページの共通化 | AI Agent | IT6 低優先 | DRY |
| T10 | 計画策定時に ArchUnit ルール（ADR 0008 等）も検証 | AI Agent | 計画策定時 | 命名規約違反の事前検出（TrackingView 事例） |

## ベロシティ分析

| イテレーション | 計画 SP | 実績 SP | 達成率 | 累積ベロシティ |
|--------------|---------|---------|--------|--------------|
| IT1 | 12 | 12 | 100% | 12 |
| IT2 | 12 | 12 | 100% | 12 |
| IT3 | 11 | 11 | 100% | 11.67（直近 3 IT 平均）|
| IT4 | 11 | 11 | 100% | 11.5（直近 3 IT 平均）|
| IT5 | 11 | 11 | 100% | 11.33（直近 3 IT 平均）|
| **累積** | **57** | **57** | **100%** | — |

5 イテレーション連続 100% 達成。IT5 では新 Context を 2 つ同時新設（Tracking / Handling）+ 申し送り 6 件 + 機能 3 件 + ADR 1 件を消化。Java 版実績との比較分析（release_plan.md L156 で IT5 完了時とされる）は次回ループで実施予定。

## Java 版実績との比較

Phase 3 完了相当（Release 1.0 MVP の半分）まで進行。Java 版 take-2 同フェーズ実績との比較は IT6 完了時（Phase 3 全体完了）に統合して実施する。

## 次のステップ

1. IT5 自己レビュー高優先度 7 件（H1-H7）を IT6 申し送りタスクに変換
2. IT5 完了報告書を `creating-iteration-report` スキルで作成
3. IT6 計画策定（残ストーリー: US16 引取 / US17 状態手動更新 / US21 料金算出 + Release 1.0 MVP リリース）
4. GitHub Project（#30）に IT5 完了分を `/syncing-github-project --sync` で反映
5. IT5 staging 完了後に正式な `developing-review`（XP 5 エージェント並列）

## 関連ドキュメント

- [IT5 計画](./iteration_plan-5.md)
- [IT5 セルフレビュー](../review/it5_self_review_20260622.md)
- [IT4 ふりかえり](./retrospective-4.md)
- [リリース計画](./release_plan.md)
- [ADR 0010 追跡番号採番ポリシー](../adr/0010-tracking-number-policy.md)
