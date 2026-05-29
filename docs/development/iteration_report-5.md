# イテレーション 5 完了報告書

## プロジェクト概要

| 項目 | 内容 |
|------|------|
| **プロジェクト名** | 国際貨物輸送管理システム（take-5） |
| **イテレーション** | IT5（追跡・荷役、Phase 2 開始） |
| **期間** | 2026-07-16 〜 2026-07-29（計画 2 週間）/ 2026-05-28〜2026-05-29（実績 2 日） |
| **ゴール** | 予約確定後の追跡番号採番から荷役記録による状態自動更新・追跡管理者の手動更新までを cross-service Saga で実装し、Phase 2 を開始する。trackingms / handlingms の 2 新規モジュールを Spring Boot 4 + Axon Kafka Extension で立ち上げる。 |

### 要員

| 役割 | 担当 |
|------|------|
| 開発者 | k2works（AI ペアプログラミング） |

## 指標

### ベロシティ

| 項目 | 値 |
|------|-----|
| 計画 SP（コミット） | 10 |
| 完了 SP | 10（US14:2 / US17:3 / US15:3 / US16:2） |
| 達成率 | 100%（コミット 10 SP すべて達成）|
| 前回ベロシティ | 11 SP（IT4） |
| 累計実績 SP | 51/76（67%、Phase 2 進行中） |

### バーンダウン（リリース）

```mermaid
xychart-beta
    title "リリースバーンダウン（実績）"
    x-axis ["開始", "IT1", "IT2", "IT3", "IT4", "IT5"]
    y-axis "残 SP" 0 --> 80
    line "実績" [76, 66, 56, 46, 35, 25]
```

Phase 1 完了（41 SP）+ IT5（10 SP）= 累計 51/76 SP（67%）。残 25 SP（Phase 2 残：IT6/IT7/IT8）。

### コミット規模

| 項目 | 値 |
|------|-----|
| コミット数 | 21（本体実装 16 + レビュー対応 5） |
| ファイル変更 | 89 ファイル |
| 行追加 | 約 7,000 行 |
| バックエンド新規モジュール | trackingms（port 8084）/ handlingms（port 8085） |
| Flyway マイグレーション | V1〜V3（trackingms）/ V1〜V3（handlingms）追加 |
| shared cross-service イベント | TrackingIssuanceRequestedEvent / CargoTrackedEvent / HandlingActivityRegisteredEvent / CargoDeliveredEvent |

## テスト結果

### バックエンド

| サービス | 全体 LINE カバレッジ | 主要新規クラス |
|---------|---------------------|---------------|
| trackingms | 94.4%（336/356） | TrackingActivity 集約 / TransportStatusTransition / TrackingController / 各 EventHandler |
| handlingms | 91.2%（218/239） | HandlingActivity 集約 / HandlingValidationService / HandlingActivityController / CargoSnapshotProjectionEventHandler |
| authms | 93.6%（IT4 末 59.9% から +33.7） | JwtTokenProvider / JwtAuthenticationFilter / AuthController / UserRepositoryImpl のテスト整備 |
| bookingms | 87.5% | BookingSagaManager（@ProcessingGroup + EventGateway/CommandGateway 注入）、Cargo 集約に AssignTrackingDetailsCommand |
| routingms | 91.7% | 変更なし |

全サービスの `gradle check`（ユニット・Axon Test・統合・ArchUnit・JaCoCo）が PASS。Kafka 統合テスト 6 件は `@Tag("kafka-integration")` で `check` から除外（IT6 で構造的解決予定）。

### テスト増分（IT5 新規追加）

| 区分 | テスト |
|------|--------|
| trackingms ドメイン | TransportStatusTransitionTest（26 件、CSV 駆動 9×9 マトリックス + null ガード + 網羅検証）、TrackingNumberTest（書式）、TrackingNumberGeneratorTest |
| trackingms 集約 | TrackingActivityAggregateTest（7 件：US14 初期化 + US17 4 件） |
| trackingms EventHandler | TrackingSummaryProjectionEventHandlerTest（5 件）/ TrackingNotificationEventHandlerTest（3 件）/ CargoDeliveredEventPublisherTest（4 件、H3 冪等性含む）/ TrackingIssuanceRequestedEventHandlerTest / HandlingActivityRegisteredEventHandlerTest（8 件、HandlingType 4 マッピング + ADR-0011 ホワイトリスト 2 + ホワイトリスト外 1） |
| trackingms Controller | TrackingControllerTest（8 件、@InjectMocks）/ TrackingControllerIntegrationTest（5 件、@DirtiesContext + TestRestTemplate） |
| handlingms 集約 | HandlingActivityAggregateTest（9 件：US15/US16 + 未来時刻拒否 + 重複拒否 + 予定外検知 2 件発行） |
| handlingms ドメインサービス | HandlingValidationServiceTest（9 件、window/duplicate/unexpected の境界） |
| handlingms EventHandler | CargoSnapshotProjectionEventHandlerTest（3 件）/ HandlingActivityProjectionEventHandlerTest（4 件、H2 メトリクス追加）/ HandlingActivityCrossServicePublisherTest（2 件） |
| handlingms Controller | HandlingActivityControllerTest（7 件） |
| Kafka 統合（Testcontainers、@Tag 分離）| 6 件（既存 2 + IT5 新規 4）：cross-service 双方向の Kafka 経由検証 |
| authms | JwtTokenProviderTest（4 件）/ JwtAuthenticationFilterTest（4 件）/ AuthControllerTest（2 件）/ AuthExceptionHandlerTest（2 件）/ UserRepositoryImplTest（5 件） |
| フロント | trackingApi（5 件 + fetch 9 件）/ TrackingListPage（4 件）/ TrackingManagePage（4 件）/ handlingApi（3 件 + fetch 7 件）/ HandlingFormPage（8 件、H4 連続入力含む）/ HandlingListPage / Navigation（+6 件、ROLE_TRACKER/HANDLER） |
| フロント合計 | **29 ファイル / 190 件 PASS** |
| E2E（Playwright）| **45 件 PASS**（既存 35 + IT5 新規 10：軽量 UI 8 / cross-service 2、`CROSS_SERVICE_E2E=1`） |

## 実施内容と評価

### ストーリー別完了状況

| ID | ストーリー | SP | 状態 |
|----|-----------|----|----|
| US14 | 予約確定後の追跡番号発行 | 2 | 完了 |
| US17 | 貨物状態を手動更新する | 3 | 完了 |
| US15 | 荷役作業を記録する | 3 | 完了 |
| US16 | 引取作業を記録する | 2 | 完了 |

### US14 受入確認

- BookingSagaManager が `BookingConfirmedEvent` を購読し、`shared.TrackingIssuanceRequestedEvent` を Kafka 経由で trackingms に発行
- trackingms `TrackingIssuanceRequestedEventHandler`（tracking processor、ADR-0011 ホワイトリスト）が `InitializeTrackingCommand` を発行
- `TrackingActivity` 集約が NOT_RECEIVED で初期化、`TrackingNumberGenerator`（SecureRandom 36 文字種 × 10 桁）で TRK- + 大文字英数 10 桁を採番
- trackingms が `shared.CargoTrackedEvent` を発行 → bookingms `@SagaEventHandler` が `AssignTrackingDetailsCommand` を Cargo に送信し、`bookingStatus` を CONFIRMED → TRACKING_ISSUED に遷移、`@EndSaga` で Saga 終了
- `tracking_summary` / `cargo_summary` の双方向投影が cross-service Kafka で End-to-End 動作（Testcontainers Kafka 統合テスト 2 件で検証）
- NotificationAcl（IT5 0.6、スタブ実装）が `notifyTrackingIssued` を呼び出し、IT6 で実メール送信に置換予定

### US17 受入確認

- `TransportStatus`（9 値）と `TransportStatusTransition`（Map 駆動 9×9 マトリックス）で許可遷移（21 + IT5 H5 で MISROUTED 救済 3 = 24）と拒否を構造化
- `UpdateTransportStatusCommand` を Cargo 集約が受理（`TransportStatusTransition.canTransition` で検証、不正遷移は `IllegalStateException`）
- 状態更新時に `TransportStatusUpdatedEvent` を発行、MISROUTED 遷移時は `CargoMisroutedEvent` を同時発行（`misrouted = true` フラグ）
- `tracking_summary` を update（current_status / current_unlocode / current_voyage_number / last_event_at）し `tracking_event` に source=MANUAL で時系列追記
- REST API（GET /api/v1/tracking、GET /api/v1/tracking/{tn}、GET /api/v1/tracking/{tn}/events、POST /api/v1/tracking/{tn}/status）と `@ExceptionHandler` による 422（IllegalState）/ 400（IllegalArgument）マッピング
- フロント S16 一覧 / S17 詳細・状態更新（許可遷移のみセレクト、misrouted バッジ、履歴時系列表示、終端ガイダンス、MISROUTED 救済警告）

### US15 受入確認

- handlingms `CargoSnapshot` ACL：bookingms の `shared.TrackingIssuanceRequestedEvent` / `CargoTrackedEvent` を Kafka tracking で購読し `cargo_snapshot` に冪等保存（origin / destination / cargoType / trackingNumber）
- `HandlingActivity` 集約が 5 種別（RECEIVE / LOAD / UNLOAD / CLAIM / CUSTOMS）と不変条件（LOAD/UNLOAD 航海番号必須、CLAIM 荷受人確認必須、未来時刻拒否、5 分粒度重複拒否、CargoSnapshot 経由の予定外検知 → `UnexpectedHandlingDetectedEvent`）を実装
- `HandlingValidationService`（ドメインサービス）が集約から `@CommandHandler` パラメータで注入され、集約越境のバリデーション（重複検知・予定外検知）を分離
- handlingms `HandlingActivityCrossServicePublisher` が local イベントを `shared.HandlingActivityRegisteredEvent` に変換して Kafka publish
- trackingms `HandlingActivityRegisteredEventHandler` が HandlingType に応じた `UpdateTransportStatusCommand` を発行（RECEIVE→RECEIVED / LOAD→LOADED / UNLOAD→UNLOADED / CLAIM→DELIVERED、CUSTOMS は状態変更なし）。ADR-0011 ホワイトリスト（AggregateNotFoundException / CommandExecutionException）で cross-service エラーを堅牢化
- REST API（POST /api/v1/handling、GET /api/v1/handling、GET /api/v1/handling/{id}、trackingNumber フィルタ対応）+ フロント S20 荷役作業記録（種別動的フォーム）/ S21 履歴
- `handling_activity` 投影で booking_id / origin / destination / cargoType を CargoSnapshot から補完（未到着時のフォールバック値発生件数を Micrometer Counter `handlingms.projection.snapshot_missing` で観測可能化）

### US16 受入確認

- `ClaimVerification` 値オブジェクト：荷受人氏名必須 + 署名参照 or 確認コードのいずれか必須
- 引取（CLAIM）登録 → trackingms `HandlingActivityRegisteredEventHandler` が `UpdateTransportStatusCommand(DELIVERED)` を発行
- `TrackingActivity` 集約が AWAITING_CLAIM → DELIVERED へ遷移、`TransportStatusUpdatedEvent` を発行
- trackingms `CargoDeliveredEventPublisher` が DELIVERED 遷移を検知して `shared.CargoDeliveredEvent` を Kafka に発行（IT7 Billing で `CalculateInvoiceCommand` のトリガー）
- 冪等性（H3 対応）：`tracking_summary.delivered_published_at` 列で「未発行のみ」更新する楽観的ロック相当。event store リプレイで二度発行されない
- フロント S20 で CLAIM 選択時に荷受人確認フィールドを動的表示し、クライアントバリデーションで「署名 or 確認コードのいずれか必須」を強制

## 設計成果

### trackingms（新規マイクロサービス、port 8084）

- **集約**：`TrackingActivity`（@Aggregate + @CommandHandler + @EventSourcingHandler、TransportStatusTransition 注入）
- **値オブジェクト**：`TrackingNumber`（`^TRK-[A-Z0-9]{10}$` 検証）、`TransportStatus`（9 値 enum）
- **ドメインサービス**：`TransportStatusTransition`（9×9 マトリックス）、`TrackingNumberGenerator`（SecureRandom）
- **イベント**：`TrackingInitializedEvent` / `TransportStatusUpdatedEvent` / `CargoMisroutedEvent`
- **EventHandler**：`TrackingSummaryProjectionEventHandler`（tracking-local-projection）/ `TrackingNotificationEventHandler`（tracking-notifications）/ `CargoDeliveredEventPublisher`（cargo-delivered-publisher）/ `TrackingIssuanceRequestedEventHandler`（cross-service tracking）/ `HandlingActivityRegisteredEventHandler`（handling-activity-events tracking）
- **NotificationAcl**：Port + LoggingNotificationAcl Adapter（スタブ）
- **Read Model**：`tracking_summary`（Flyway V2 + V3 で delivered_published_at 追加）/ `tracking_event`（時系列）/ `tracking_exception`（IT6 用に先行作成）
- **REST**：GET /tracking、GET /tracking/{tn}、GET /tracking/{tn}/events、POST /tracking/{tn}/status

### handlingms（新規マイクロサービス、port 8085）

- **集約**：`HandlingActivity`（@Aggregate、HandlingValidationService 注入による集約越境バリデーション）
- **値オブジェクト**：`ClaimVerification`（record、署名 or コード必須）、`HandlingType`（5 値 enum）
- **ドメインサービス**：`HandlingValidationService`（重複検知 + 予定外検知）
- **イベント**：`HandlingActivityRegisteredEvent`（local）/ `UnexpectedHandlingDetectedEvent`（警告）
- **EventHandler**：`CargoSnapshotProjectionEventHandler`（cargo-snapshot tracking、cross-service 入力）/ `HandlingActivityProjectionEventHandler`（local）/ `HandlingActivityCrossServicePublisher`（handling-cross-service-publish、local → shared 変換 publish）
- **Read Model**：`cargo_snapshot`（Flyway V3）/ `handling_activity` / `handling_itinerary_snapshot` / `claim_verification`
- **REST**：POST /handling、GET /handling、GET /handling/{id}

### bookingms（拡張）

- `BookingSagaManager`：@ProcessingGroup("booking-saga") + transient EventGateway/CommandGateway 注入で TrackingIssuanceRequestedEvent publish と CargoTrackedEvent 受信
- `Cargo` 集約：`AssignTrackingDetailsCommand` ハンドラ追加（CONFIRMED → TRACKING_ISSUED、`CargoTrackingAssignedEvent`）
- Flyway V8（cargo_summary.tracking_number + UNIQUE インデックス）
- `KafkaEventProcessingConfig` に booking-saga プロセッサ登録（cross-service イベント購読）

### shared

- `TrackingIssuanceRequestedEvent`（bookingms → trackingms）
- `CargoTrackedEvent`（trackingms → bookingms）
- `HandlingActivityRegisteredEvent`（handlingms → trackingms、ClaimVerificationData record 併載）
- `CargoDeliveredEvent`（trackingms → billingms 予定、IT5 4.2）

### フロント

- features/tracking（S16 一覧 / S17 詳細・状態更新）+ ALLOWED_TRANSITIONS（バックエンド整合 + H5 MISROUTED 救済）
- features/handling（S20 荷役作業記録 / S21 履歴、種別動的フォーム、CLAIM 動的フィールド、H4 連続入力 + ログインユーザー初期化）
- Navigation 拡張（ROLE_TRACKER + ROLE_HANDLER）
- App.tsx ルーティング追加（/tracking、/tracking/:tn/manage、/handling、/handling/new）

## 品質ゲート

- **JaCoCo**：新規コードの行カバレッジは全サービス 80% 以上（trackingms 94.4% / handlingms 91.2% / authms 93.6%）
- **SonarQube Quality Gate ライブスキャン実施済み（2026-05-29）**：backend / frontend 両プロジェクト **PASS**。
  - backend: new_coverage **91.7%** / 重複 0.40% / new_violations 0（Bug 0・Vulnerability 0・Code Smell 0・Security Hotspot 0、全体カバレッジ 88.0%）
  - frontend: new_coverage **75.7%** / 重複 0.0% / new_violations 0（Code Smell 0、全体カバレッジ 78.1%）
- **マルチパースペクティブレビュー実施**（5 エージェント並列：programmer / tester / architect / technical-writer / user-representative）。指摘 高 7 件 / 中 10 件 / 低 12 件 中、**重要度「高」7 件はすべて IT5 内で対応完了**（H1: release_plan 反映 / H2: フォールバック Counter 化 / H3: CargoDeliveredEvent 冪等化 / H4: 連続入力 + ログインユーザー / H5: MISROUTED 救済 / H6: 統合テスト厳密化 / H7: Kafka 統合テスト @Tag 分離）
- 詳細は [IT5_review_20260529.md](../review/IT5_review_20260529.md)

## ADR

| ADR | タイトル | IT5 での適用 |
|-----|---------|-------------|
| [ADR-0009](../adr/0009-cross-service-event-coordination.md) | cross-service イベント連携と Axon Saga | bookingms ↔ trackingms 双方向 + handlingms → trackingms の cross-service Saga に適用 |
| [ADR-0010](../adr/0010-local-h2-kafka-topic-initialization.md) | local-h2 トピック初期化・孤児イベント冪等スキップ | cross-service ハンドラの再処理耐性に継承 |
| [ADR-0011](../adr/0011-kafka-tracking-error-handling-policy.md) | Kafka tracking エラーハンドリングのホワイトリスト方式 | 全 cross-service ハンドラ（TrackingIssuanceRequested / HandlingActivityRegistered）で AggregateNotFoundException + CommandExecutionException のみ WARN スキップ |

## フェーズ・累計進捗

### Phase 1 + IT5 進捗

| イテレーション | 計画 SP | 実績 SP | 状態 |
|---------------|---------|---------|------|
| IT1 | 10 | 10 | 完了 |
| IT2 | 10 | 10 | 完了 |
| IT3 | 10 | 10 | 完了 |
| IT4 | 11 | 11 | 完了 |
| **Phase 1 計** | **41** | **41** | **完了（Release 1.0 MVP）** |
| IT5 | 10 | 10 | **完了（2026-05-29、Phase 2 開始）** |
| **累計** | **51** | **51** | **67%（残 25 SP / IT6-IT8）** |

### Phase 2 残

| イテレーション | 計画 SP | 範囲（予定） |
|---------------|---------|-------------|
| IT6 | 9 | US18 公開追跡照会（時限署名トークン）+ US19/US20 例外処理（遅延・破損・紛失） |
| IT7 | 8 | US21-US23 精算（料金算出・割引適用・請求書発行・入金確認） |
| IT8 | 8 | 非機能改善・運用機能 |

## 残課題（IT6 以降のフォローアップ）

### 構造的課題（IT5 ふりかえり Try）

- **T1（最優先）**：Testcontainers Reusable + 一意 topic prefix で Kafka container race を構造的に解決し、`@Tag("kafka-integration")` 除外を解除（IT6 序盤）
- **T2**：cross-service 冪等性・トランザクション境界の方針 ADR 化（H3 と既存 confirm publish/updateStatus 問題を統合）
- **T3**：`@ProcessingGroup` 命名規約 ADR 化（cross- / local- / outbound- prefix 統一）
- **T5**：handlingms フォールバック投影の根本対処（DLQ 風待避テーブル）
- **T6**：フロント型生成の OpenAPI 自動化 ADR

### 業務適合（IT6 / IT7 で要件追加）

- 通知配信記録の UI 可視化（NotificationAcl 発火履歴を S17 に表示）
- ROLE_SALES への読み取り専用追跡確認画面
- S20 のバーコード / QR スキャナ対応
- S17 EXCEPTION 復帰時の判断補助

### バックログ（中・低指摘 22 件、GitHub Issue 化推奨）

- programmer 系：shared HandlingTypeCode 化（M1）/ Controller 非同期化（M2）/ Clock 注入（L1）/ @ExceptionHandler 共通化（L2）など
- architect 系：BookingSagaManager itinerary 型を shared LegData に（M3）/ shared HandlingActivityRegisteredEvent 改名（L6）
- writer 系：architecture_backend API カタログ 3 行追記（M5）
- tester 系：Clock 注入（M9）/ Mock 暗黙前提（M10）/ E2E helper 抽出（L12）

## ふりかえり

詳細は [retrospective-5.md](./retrospective-5.md) を参照。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-29 | 初版作成（IT5 完了、Phase 2 開始、10/10 SP、累計 51/76 SP・67%、マルチパースペクティブレビュー高 7 件すべて対応済み、両プロジェクト Quality Gate OK） | k2works |
