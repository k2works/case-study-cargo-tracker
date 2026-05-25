# ADR-0009: cross-service イベント連携と Axon Saga を採用する（Kafka tracking モード）

bookingms ⇔ routingms ⇔ trackingms 間の業務連携を、Kafka tracking モードの cross-service ドメインイベントと `BookingSagaManager`（Axon Saga）によるオーケストレーションで実現します。

日付: 2026-05-25

## ステータス

提案中（IT3 で承認）

## コンテキスト

IT2 までに bookingms（荷主・予約）を実装しましたが、各マイクロサービスは Axon イベントをサービス内で完結処理しており、サービスをまたぐイベント連携と長期業務トランザクションの調整機構が未整備でした。

- **設計と実装のドリフト**: ADR-0001 は Axon Kafka の設定例で `event-processor-mode: tracking` と `JdbcTokenStore` / `JdbcSagaStore` を提示していましたが、IT2 の実装は subscribing モード相当（サービス内完結）で Saga も未導入でした（`retrospective-2.md` 技術的負債 #9）。
- **IT3 の要件**: US06（予約情報を経路設計者に引き渡す: bookingms → routingms へ経路設計依頼）と US13（予約確定 → 追跡番号発行依頼）は、サービスをまたぐイベントを必要とします。
- **ドメインモデルの想定**: `domain-model.md` は「Saga（`BookingSagaManager`）で経路割当 → 追跡番号発行まで連動する」と明記しており、設計上は Saga が前提です。
- **長期業務トランザクション**: 予約ライフサイクル（仮受付 → 経路設計中 → 経路提案 → 予約確定 → 追跡番号発行）は複数サービス・長時間にわたるため、中央調整・補償・タイムアウト制御が必要です。
- **インフラ前提**: `data-model.md` の `booking_read_db` には既に `token_entry` / `saga_entry` / `association_value_entry` が含まれており、tracking モードと Saga Store の受け皿は用意されています。

これらにより、IT3 でメッセージング方式（subscribing → tracking）とサービス間連携・調整方式（Saga）を確定する必要があります。

### 候補評価

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| cross-service イベント + Axon Saga（採用） | 設計通りの CQRS+Saga、疎結合、補償・順序保証、Axon が Saga ライフサイクルを提供 | tracking トークン・Saga Store の運用、結果整合性、テスト複雑化 |
| 同期 REST 呼び出し | 実装が直感的、即時整合 | サービス結合度・可用性悪化、CQRS/ES の非同期性を損なう |
| コレオグラフィ（中央調整なし） | 単純なイベント連鎖に最適、調整役が不要 | 長期トランザクションの順序保証・補償・タイムアウトが困難 |

## 決定

**Kafka tracking モードの cross-service ドメインイベントと `BookingSagaManager`（Axon Saga）を採用します。**

具体的には以下のとおりとします。

1. **イベントプロセッサ**: bookingms / routingms の event processor を **tracking モード** に移行します（ADR-0001 の設定例を実装で具体化）。進捗は `token_entry` で管理します。
2. **cross-service ドメインイベント**: 次のイベントを Kafka トピック経由で発行・購読します。
   - `RouteDesignRequestedEvent`（bookingms → routingms、US06: 経路設計依頼）
   - `CargoRoutedEvent`（routingms → bookingms、IT4: 経路確定）
   - `TrackingIssuanceRequestedEvent`（bookingms → trackingms、US13 → IT5: 追跡番号発行依頼）
3. **オーケストレーション**: `BookingSagaManager`（Axon Saga）で予約ライフサイクルを調整します。
   - `@StartSaga` — `CargoBookedEvent`（予約登録、IT2）
   - `RouteDesignRequestedEvent` → routingms へ経路設計を指示（US06）
   - `CargoRoutedEvent` → 予約を `ROUTE_PROPOSED` へ（IT4）
   - `BookingConfirmedEvent` → `TrackingIssuanceRequestedEvent` を発行（US13）
   - `@EndSaga` — `CargoTrackedEvent`（追跡番号発行、IT5）または `BookingCancelledEvent`
4. **Saga Store / Token Store**: `JdbcSagaStore` / `JdbcTokenStore`（ADR-0001、`booking_read_db` の `saga_entry` / `association_value_entry` / `token_entry`）。
5. **責務境界**: 読み取りモデル投影（Projection）はコレオグラフィ（各サービスが自前のイベントを購読）、長期業務トランザクションは Saga（オーケストレーション）と使い分けます。

### 変更箇所

- `apps/backend/bookingms/.../saga/BookingSagaManager.java`（新規）
- `apps/backend/bookingms/.../domain/event/RouteDesignRequestedEvent.java`、`TrackingIssuanceRequestedEvent.java`（新規）
- `apps/backend/bookingms/.../application/CargoCommandService.java`（cross-service イベント発行）
- `apps/backend/routingms/.../` cross-service イベントの購読ハンドラ
- Kafka 設定（`event-processor-mode: tracking`、トピック設計）
- Testcontainers Kafka による cross-service 疎通統合テスト

### 代替案

- 代替案 1: 同期 REST 呼び出し（bookingms → routingms を直接 HTTP）
  却下理由: サービス結合度が上がり、routingms 停止時に予約引き渡しが失敗します。Axon Kafka（ADR-0001）で確立した非同期・イベント駆動の方針と整合せず、CQRS/ES の利点を損ないます。
- 代替案 2: subscribing モードのまま個別イベントハンドラで cross-service 連携
  却下理由: 予約ライフサイクルのような長期・複数サービスのトランザクションでは、状態整合・補償・タイムアウトを各ハンドラに分散実装することになり破綻します。中央調整役（Saga）が必要です。
- 代替案 3: コレオグラフィのみ（中央調整なしのイベント連鎖）
  却下理由: 単純な投影更新には適しますが、「経路設計依頼 → 経路確定 → 確定 → 追跡番号発行」の順序保証・補償・期限切れ処理を表現できません。投影はコレオグラフィ、業務トランザクションは Saga と使い分けます。
- 代替案 4: 独立したオーケストレーションサービス（別マイクロサービス / BPMN エンジン）
  却下理由: 1 名開発・MVP 規模に対しオーバーエンジニアリングです。Axon Saga が Saga ライフサイクル・アソシエーション・永続化を提供するため十分です。
- 代替案 5: Outbox パターン + 独自イベントリレー
  却下理由: Axon Kafka Extension が publish/consume と冪等な処理を提供しており、独自リレーは車輪の再発明になります。

## 影響

### ポジティブ

- `domain-model.md` が想定する CQRS + Saga 構造を実現し、設計と実装のドリフト（技術的負債 #9）を解消します。
- サービス間が疎結合になり、イベント駆動で可用性が向上します。
- Saga により補償トランザクション・順序保証・タイムアウト制御が可能になります。
- tracking モードによりイベント再生・冪等処理・進捗トークン管理が得られます。

### ネガティブ

- tracking モードのトークン（`token_entry`）と Saga Store（`saga_entry` / `association_value_entry`）の運用・監視が必要になります。
- Kafka トピック設計・パーティション設計・順序保証の責任がチームに生じます（ADR-0001 のネガティブを継承）。
- 結果整合性により、cross-service 反映までの一時的な状態不一致（UI 表示ラグ）が発生します。
- Saga のテストが複雑化します（`SagaTestFixture` + Testcontainers Kafka により CI 時間が増加）。
- subscribing → tracking 移行で IT2 の既存イベント処理が退行するリスクがあります（移行前後の回帰テストが必須）。

## コンプライアンス

次を満たすことで、決定の実装完了を確認します。

- bookingms が `RouteDesignRequestedEvent` を Kafka に発行し、routingms が購読して処理できること（Testcontainers Kafka 統合テスト）。
- `BookingSagaManager` が `CargoBookedEvent` で開始し、`BookingConfirmedEvent` / `BookingCancelledEvent` まで状態遷移すること（`SagaTestFixture` ユニットテスト）。
- event processor が tracking モードで動作し、`token_entry` に進捗トークンが記録されること。
- IT2 の荷主・予約登録 E2E（`booking.spec.ts`）が tracking 移行後も回帰 PASS すること。
- `./gradlew test` のユニット・統合テストが全 PASS し、SonarQube Quality Gate が PASS すること。

## 備考

- 著者: k2works
- 関連 ADR: ADR-0001（Axon Kafka + Aiven 採用。tracking モードと `JdbcSagaStore` を計画）、ADR-0002（MyBatis 採用）
- 関連ストーリー: US06（予約引渡し）、US13（予約確定）
- 関連ドキュメント: `docs/development/iteration_plan-3.md`（タスク 1: cross-service イベント基盤）、`docs/design/domain-model.md`（`BookingSagaManager`）、`docs/design/data-model.md`（`saga_entry` / `token_entry`）
