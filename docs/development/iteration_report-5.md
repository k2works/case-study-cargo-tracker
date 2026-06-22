---
title: イテレーション 5 完了報告書
date: 2026-06-22
---

# イテレーション 5 完了報告書

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT5 |
| 期間 | 2026-08-17 〜 2026-08-30（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | US14 追跡番号発行、US15 荷役作業記録、US18 追跡情報照会の 3 ストーリー（計 11 SP）を完成させ、Tracking Context + Handling Context を新設し、IT4 マルチパースペクティブセルフレビュー高優先度 6 件（H1-H6）を解消する |
| 計画 SP | 11（US14: 2 + US15: 6 + US18: 3） |
| 実績 SP | 11 |
| 達成率 | 100% |

## ストーリー実績

| ID | ストーリー | 状態 | 計画 SP | 実績 SP |
|----|-----------|------|---------|---------|
| US14 | 追跡番号を発行する | ✅ 完了 | 2 | 2 |
| US15 | 荷役作業を記録する | ✅ 完了 | 6 | 6 |
| US18 | 追跡情報を照会する | ✅ 完了 | 3 | 3 |
| **合計** | | | **11** | **11** |

## タスク実績

25 タスクすべて完了（IT4 申し送り 0.x: 6 件、US14 1.1-1.5: 5 件、US15 2.1-2.10: 10 件、US18 3.1-3.4: 4 件）。

### IT4 申し送り（0.x）

| # | タスク | 完了内容 |
|---|--------|---------|
| 0.1 | NotificationPayload 値オブジェクト導入（H1）| `booking/domain/model/valueobjects/NotificationPayload`（sealed trait + 3 サブクラス）を新設し、Play JSON シリアライズは `booking/application/notifications/NotificationPayloadJson` に隔離（ArchUnit ルール 1 整合）。`BookingCommandService.logNotification` と `NotifyRouteCommandService.buildPayload` の文字列ハードコーディングを撤廃 |
| 0.2 | transition ヘルパ統一 + CargoErrorMessages 抽出（H2）| `BookingCommandService.assignToRouting` / `assignItinerary` を `transition` ヘルパ経由に統一、`Cargo.Error → メッセージ` 変換を `application/errors/CargoErrorMessages` に抽出。重複コード 4 箇所削減 |
| 0.3 | parseVoyages を traverse 化 + persistConfirmed 簡素化（H3）| `foldLeft + prepend + reverse` の O(n) 純粋実装で `:+` の O(n²) を解消、`persistConfirmed` の Some/None 分岐内 save 重複を for 内包で一直線化（27 行 → 17 行） |
| 0.4 | 経路紐付け整合性 E2E 追加（H4）| 確定後の `RouteCandidateSelection.voyages` と `Cargo.itinerary.voyageNumbers` の一致を E2E で検証、コンテキスト分離（BookingCommandService 単独呼出時の Routing 集約不在）も E2E 実証 |
| 0.5 | 通知 payload を JSON 構造アサーション化（H5）| `NotifyRouteCommandServiceSpec` に voyages 単一ケース + 5 キー構造アサーション、`BookingCommandServiceSpec` に confirm / cancel の Play JSON パース検証を追加。部分文字列マッチ全廃 |
| 0.6 | デシジョンテーブル + notify べき等性（H6）| `cancel` の Preliminary / RouteProposed / RouteAssigned / Confirmed 4 状態網羅、`notify` のべき等性仕様（追記型、重複抑止しない）をテストで固定 |

### US14 追跡番号発行（2 SP）

| # | タスク | 完了内容 |
|---|--------|---------|
| 1.1 | Tracking Context 骨格 + ADR 0010 | `TrackingActivity` 集約 + `TrackingNumber` / `TrackingBookingId` opaque type + `TrackingStatus` 9 値 + `TrackingActivityRepository` ポート。ADR 0010 で採番ポリシー（`TN-` + 6 桁、UUID 不採用理由含む）と集約境界を確定 |
| 1.2 | Flyway V12 | tracking_activity（UNIQUE 制約 / 楽観ロック / 監査）+ cargo.tracking_number カラム + notification_log CHECK 拡張（TrackingIssued 追加）|
| 1.3 | issueTracking ロジック | `Cargo.issueTracking(trackingNumber)`: Confirmed → TrackingIssued 遷移 + 冪等性（再発行禁止）/ `BookingCommandService.issueTracking` で TrackingIssued 通知ログ記録 / `TrackingCommandService.assign` で採番 + TrackingActivity 作成 + 冪等性 |
| 1.4 | 予約詳細画面 UI | Confirmed 状態時のみ「追跡番号を発行」ボタン表示、発行後はバッジ + 公開追跡 URL リンク表示、POST `/bookings/:bookingId/issue-tracking`（PRG）|
| 1.5 | E2E + ユニットテスト | 採番 / TrackingActivity 初期 NotReceived / Cargo.trackingNumber 永続化 / NotificationType.TrackingIssued ログ / 冪等性 |

### US15 荷役作業記録（6 SP）

| # | タスク | 完了内容 |
|---|--------|---------|
| 2.1 | Handling Context 骨格 | `HandlingActivity` 集約 + `HandlingType` enum（5 値 + `requiresVoyage` 内包）+ `HandlingVoyageNumber` opaque type + `HandlingActivityRepository` ポート |
| 2.2 | Flyway V13 | handling_activity（event_type CHECK / インデックス 3 つ）|
| 2.3 | TrackingActivityEvent + addEvent | 集約配下子エンティティ、時系列順序検証（最終イベントより過去の時刻を拒否）、`deriveStatus` でイベント履歴から TrackingStatus 9 値を導出 |
| 2.4 | Flyway V14 | tracking_handling_event（tracking_activity FK CASCADE）+ notification_log CHECK 拡張（HandlingRecorded 追加）|
| 2.5 | HandlingCommandService.register | 入力検証 + HandlingActivity 集約生成・永続化（ArchUnit ルール 3 準拠で他 Context へは依存しない）|
| 2.6 | Tracking 側 recordEvent | `TrackingCommandService.recordEvent` で `appendEvent` 経由イベント追記、楽観ロックで更新整合性保証 |
| 2.7 | ルート逸脱警告 | Itinerary に leg 詳細未対応のため `false` 固定（IT6 で `Itinerary` 拡張後に再評価、コメントで明記） |
| 2.8 | 荷役通知 | `NotificationPayload.HandlingRecorded` ADT 追加、`BookingCommandService.logHandlingNotification` で NotificationType.HandlingRecorded ログ |
| 2.9 | 荷役画面 | `/handling` 一覧（検索・経路逸脱警告バッジ表示）+ `/handling/new` 登録フォーム（種別ラジオ Receive/Load/Unload / 日時 datetime-local / UN/LOCODE / 航海番号 / 作業員名）|
| 2.10 | E2E + 単体テスト | Receive ハッピーパス / Receive→Load 状態自動更新（Received→Loaded）/ 存在しない追跡番号エラー |

### US18 追跡情報照会（3 SP）

| # | タスク | 完了内容 |
|---|--------|---------|
| 3.1 | TrackingQueryService + Read Model | `TrackingResult`（ADR 0008 命名規約準拠）/ `findByTrackingNumber(rawTn)` で TrackingNumber バリデーション込み |
| 3.2 | 認証ユーザー向け画面 | `/tracking` 入力フォーム + `/tracking/:trackingNumber` 詳細（ステータスバッジ + 履歴）+ `/tracking/:trackingNumber/timeline` htmx 30 秒ポーリング |
| 3.3 | 公開照会画面 | `/public/tracking/:trackingNumber`（AuthFilter の `/public/` 公開パスで未認証許可）、ナビ簡素化テンプレート + 404 用テンプレート |
| 3.4 | E2E | 公開 200 + ハッピーパス / 公開 404 「追跡番号が見つかりません」 / 認証 `/tracking/:trackingNumber` redirects `/login` |

## 品質メトリクス

| 指標 | 計測値 | 目標 | 判定 |
|------|--------|------|------|
| テスト総数 | 323 件 | – | – |
| テスト成功率 | 100%（323/323）| 100% | ✅ |
| Suites | 63 件 | – | – |
| ArchUnit ルール | 5/5 緑 | 5/5 | ✅ |
| マイグレーション | V1-V14 適用済 | – | ✅ |
| scalafmt / scalafix | ✅ | – | ✅ |
| 新コンテキスト | Tracking / Handling の 2 つ新設 | – | – |

## ADR 実績

| ADR | タイトル | ステータス |
|-----|---------|-----------|
| 0010 | 追跡番号採番ポリシー（`TN-` プレフィクス + 6 桁シーケンス、UUID 不採用）と Tracking Context 集約境界 | 提案 |

## マイグレーション実績

| バージョン | 内容 |
|----|------|
| V12 | tracking_activity + cargo.tracking_number + notification_log CHECK 拡張（TrackingIssued）|
| V13 | handling_activity（Handling Context 集約ルート）|
| V14 | tracking_handling_event（Tracking Context 集約内イベント）+ notification_log CHECK 拡張（HandlingRecorded）|

## 主要な設計判断

| 論点 | 判断 | 理由 |
|------|------|------|
| 追跡番号採番方式 | `TN-` + 6 桁シーケンス（BIGSERIAL 整形）| `tracking_number VARCHAR(20)` 制約に整合、業務担当者に読み取りやすい。UUID v4 36 文字は不採用（ADR 0010）|
| HandlingActivity と TrackingActivityEvent の分離 | Handling Context = 業務記録 / Tracking Context = 追跡履歴 | ドメインモデル設計（domain-model.md L150 / L775）準拠。Controller 層で順次オーケストレーション |
| TrackingActivityRepository.appendEvent 専用メソッド | save の DELETE+INSERT を避ける | Receive 後の Load で既存イベント消失バグを race condition 発見時に修正 |
| `Cargo.trackingNumber` を `Option[String]` で保持 | コンテキスト型分離は IT6 で再評価 | 文字列の denormalized lookup として data-model.md L732 に整合。型分離は self-review H2 として申し送り |
| 公開 URL `/public/tracking/:trackingNumber` を未認証で許可 | 既存 AuthFilter の `/public/` プレフィクス公開パスで自動許可 | 専用 Controller（`PublicTrackingController`）を分離して認証必要画面と物理隔離 |
| HandlingController で 3 つの CommandService を順次呼出 | ArchUnit ルール 3（コンテキスト間 application 依存禁止）を維持 | IT4 の RouteCandidateController と同じパターン。分散トランザクション境界の課題は self-review H3 で申し送り |
| TrackingView → TrackingResult リネーム | ADR 0008（queryservices 命名規約）準拠 | テストで命名規約違反を検出し即修正。計画時の検証漏れは Try T10 |

## IT4 セルフレビュー H1-H6 対応マッピング

| ID | 対応タスク | 完了 |
|----|---------|------|
| H1 通知 JSON ハードコーディング | 0.1 NotificationPayload ADT | ✅ |
| H2 BookingCommandService 重複 | 0.2 transition 統一 + CargoErrorMessages | ✅ |
| H3 parseVoyages O(n²) | 0.3 traverse 化 + persistConfirmed 簡素化 | ✅ |
| H4 経路紐付け整合性検証欠落 | 0.4 整合性 E2E 2 件追加 | ✅ |
| H5 payload 部分文字列マッチ | 0.5 JSON 構造アサーション化 | ✅ |
| H6 状態遷移網羅漏れ | 0.6 デシジョンテーブル + べき等性仕様 | ✅ |

## IT5 セルフレビュー要約（IT6 申し送り）

| ID | 観点 | 重大度 | 申し送り Try |
|----|------|-------|--------------|
| H1 | TrackingActivity.addEvent 後の version 非整合 | 高 | T1 appendEvent 戻り値変更 |
| H2 | Cargo.trackingNumber が Option[String] 生型 | 高 | T5 BookingTrackingNumber opaque type |
| H3 | HandlingController 分散トランザクション境界 | 高 | T4 orchestration サービス + 単一トランザクション |
| H4 | addEvent OutOfOrder 境界値テスト欠落 | 高 | T3 ユニットテスト追加 |
| H5 | appendEvent 楽観ロック衝突未検証 | 高 | T3 integration test 追加 |
| H6 | CargoSnapshot ACL 未実装 | 高 | T2 ACL VO + Cargo 状態検証 |
| H7 | transport_status キャッシュ整合性 | 高 | T7 Read Model 分離 or assertion |

詳細は [IT5 セルフレビュー](../review/it5_self_review_20260622.md) 参照。

## 次のステップ

1. IT6 計画策定（残ストーリー: US16 引取 / US17 状態手動更新 / US21 料金算出 + Release 1.0 MVP リリース）
2. IT5 自己レビュー高優先度 7 件（H1-H7）を IT6 申し送りタスクに変換
3. GitHub Project（#30）に IT5 完了分を `/syncing-github-project --sync` で反映
4. IT5 staging 完了後に正式な `developing-review`（XP 5 エージェント並列）

## 関連ドキュメント

- [IT5 計画](./iteration_plan-5.md)
- [IT5 ふりかえり](./retrospective-5.md)
- [IT5 セルフレビュー](../review/it5_self_review_20260622.md)
- [ADR 0010 追跡番号採番ポリシー](../adr/0010-tracking-number-policy.md)
- [リリース計画](./release_plan.md)
