# ADR-0015: billingms の cross-service イベント連携と ShipperInfo ACL

IT7 で精算サブドメイン（billingms）を新規立ち上げるにあたり、(1) **cargoms → billingms の配送完了起点での精算開始** をどう連携するか、(2) **法人割引率（Shipper の `discountRate`）を bookingms からどう参照するか** の 2 点について方針を統一する。本 ADR では (1) を **集約発火型 + Kafka tracking event 購読**（ADR-0012 準拠）、(2) を **REST 同期参照 + Resilience4j circuit breaker + Caffeine cache (TTL 5min) + 手動入力 fallback** に決定する。

日付: 2026-06-04

## ステータス

部分実装済み（IT7 完了 2026-06-05）

| 範囲 | ステータス | 備考 |
|------|----------|------|
| cross-billing event 購読（CargoDeliveredEvent → CalculateInvoiceCommand）| ✅ IT7 完了 | `CrossCargoDeliveredEventHandler` + 決定論的 invoiceId（review M1 architect） |
| local-billing 投影 | ✅ IT7 完了 | `InvoiceProjectionsEventHandler` + `InvoiceProjection` 集約クラス（review M1） |
| outbound-billing-notification | ✅ IT7 完了 | `InvoiceNotificationEventHandler` + `LoggingNotificationAcl` スタブ |
| cross-booking-billing（bookingms 側）| ✅ IT7 完了 | `CrossBillingPaymentHandler` + shared `PaymentRecordedEvent` |
| ShipperInfoAcl（Stub）| ✅ IT7 完了 | `StubShipperInfoAcl`（CORPORATE 15%） |
| ShipperInfoAcl（Rest + Resilience4j + Caffeine）| 🚦 IT8 持ち越し | RestShipperInfoAcl 実装、ADR-0015 §追加依存セクション参照 |
| SendGridNotificationAcl | 🚦 IT8 持ち越し | ADR-0018 で詳細決定 |

## コンテキスト

IT7 計画（`docs/development/iteration_plan-7.md`）で billingms を Billing Context として新規立ち上げる。`Invoice` 単一集約を中核に、`CalculateInvoice → ApplyDiscount → IssueInvoice → RecordPayment → MarkOverdue` のステートマシンを持つ（domain-model.md L885-958、data-model.md L684-760）。

このとき、billingms は他コンテキストとの連携が複数必要になる:

1. **配送完了起点（cross-service input）**: trackingms の `TrackingActivity` 集約が `DELIVERED` 遷移したときに、Invoice の生成（`CalculateInvoiceCommand`）を起動する
2. **荷主種別・契約割引率の取得（cross-service query）**: 法人割引適用時に bookingms の `Shipper` 集約から `ShipperType` と `discountRate` を取得する
3. **入金完了の波及（cross-service output）**: Invoice が `PAID` に遷移したときに、bookingms の `Cargo` 集約に `SETTLED` を伝播する（本 ADR の主題は (1)(2)。(3) は ADR-0012 集約発火型に準拠する派生）

ADR-0012「集約発火型に統一」の方針が確定し IT7 T2 で `CargoDeliveredEventPublisher` 廃止により実装と整合した（commit 6837f495）ため、(1) の入力経路は ADR-0012 を踏襲できる。一方 (2) は **同期参照 vs イベント駆動レプリケーション** という新規論点があり、本 ADR で方針を固める。

### 課題

**(1) cargoms → billingms 起点の連携**: 4 つの選択肢を比較する。

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| **(1a) Kafka tracking event + cross-billing handler（採用）** | ADR-0012 集約発火型に整合、リプレイで自然冪等、Saga 不要 | billingms 側で `CrossCargoDeliveredEventHandler` を実装する初期コスト |
| (1b) Saga (`InvoiceSagaManager`) で経路全体を制御 | 状態遷移を 1 箇所で可視化 | Saga 寿命が長い（精算完了まで継続）、IT5 の `BookingSagaManager` の運用負荷が再発 |
| (1c) Webhook（trackingms → billingms HTTP）| シンプル、Kafka 不要 | 配信保証なし、リトライ実装が必要、冪等化負荷 |
| (1d) Outbox パターン + 専用配信ワーカー | 配信保証が高い | テーブル + ワーカー実装の追加負荷 |

**(2) bookingms → billingms の Shipper 情報参照**: 4 つの選択肢を比較する。

| 候補 | 長所 | 短所 |
| :--- | :--- | :--- |
| **(2a) REST 同期参照 + circuit breaker + cache（採用）** | 実装シンプル、bookingms 側に追加責務不要、リアルタイム性確保 | bookingms 障害が billingms に伝播するリスク → CB + fallback で吸収 |
| (2b) `ShipperRegisteredEvent` / `ShipperUpdatedEvent` を Kafka で全 Shipper レプリケート | 障害分離、レイテンシ低 | 全データ複製の運用負荷、整合性遅延 |
| (2c) BFF レイヤで bookingms を join | フロントエンドで結合 | バックエンド精算ロジック内で割引を計算できない |
| (2d) bookingms の Shipper テーブルを billingms から直接 SELECT | 高速 | DB 共有でバウンデッドコンテキスト分離が破綻 |

## 決定

### 1. cargoms → billingms は Kafka tracking event 購読 + 集約発火型に統一する（ADR-0012 派生）

trackingms の `TrackingActivity` 集約が DELIVERED 遷移時に集約発火する `CargoDeliveredEvent`（shared kernel、IT7 T2 で確立）を、billingms の **`CrossCargoDeliveredEventHandler`**（`@ProcessingGroup("cross-billing")`、ADR-0014 命名規約）が Kafka tracking event として購読し、`CalculateInvoiceCommand` を発行する。

```text
[trackingms]                         [Kafka]                  [billingms]
TrackingActivity                    cargo-events              CrossCargoDeliveredEventHandler
  .apply(CargoDeliveredEvent) ──→ ─────────────────→         .on(CargoDeliveredEvent)
                                                                ↓
                                                              commandGateway.send(
                                                                CalculateInvoiceCommand(invoiceId, bookingId, ...))
                                                                ↓
                                                              Invoice.handle(CalculateInvoiceCommand)
                                                                if (billingStatus != null) return;  ← 集約内冪等化
                                                                .apply(InvoiceCalculatedEvent)
```

冪等化は **集約内のステート判定（`if (billingStatus != null) return;`）** で行う。Kafka at-least-once 配信や event store リプレイで重複到達しても、Invoice 集約が既に CALCULATED 以降の状態ならノーオペレーション。**ADR-0012 規約 1 / 2 に完全準拠**。

### 2. ShipperInfo は REST 同期参照 + Resilience4j circuit breaker + Caffeine cache (TTL 5min) で取得する

法人割引率（`ShipperType` + `CorporateContract.discountRate`）は bookingms の `Shipper` 集約に保持される。billingms は **`RestShipperInfoAcl`** で REST API（`GET /api/v1/shippers/{id}`）を同期参照する。耐障害性は次の 3 層で確保する。

| 層 | ライブラリ | 設定 |
| :--- | :--- | :--- |
| Cache | Caffeine | TTL 5 分（業務影響を許容できる古さ。割引率変更は契約締結時の稀な変更、5 分の遅延は許容）|
| Circuit Breaker | Resilience4j | 失敗率 50% / 直近 10 リクエスト window でオープン、30 秒後に half-open |
| Fallback | アプリ層 | CB オープン時は `ShipperInfoAcl.unavailable()` を返し、フロント S23 で「割引率取得失敗、手動入力で続行」UI を提示 |

```text
[billingms Invoice 集約]
   ↓ FinalizeInvoiceCommand 処理時
[CorporateDiscountPolicy.apply(basicFee, contract)]
   ↓ contract 取得
[RestShipperInfoAcl.getContract(shipperId)]
   ├─ Caffeine cache hit → 即返却
   └─ cache miss →
       ├─ CB closed → GET /api/v1/shippers/{id} →
       │              成功: cache 投入 + 返却
       │              失敗: CB がカウント、必要なら open
       └─ CB open  → CorporateContract.unavailable() を返却
                     → フロント S23 で「手動入力 fallback」UI 提示
```

### 3. 全 Shipper のイベントレプリケーションは IT7 では行わない

ADR-0009 で確立した「shared kernel イベントを Kafka 経由で配信」のパターンを Shipper には適用しない。理由:

- billingms の Shipper 参照は **精算時にのみ発生**（DELIVERED 後）。配送 1 件あたり Shipper 参照 1 回程度のため低頻度
- 全 Shipper レプリケーションは **エンティティ単位の整合性遅延** を増やす（更新後 5 分以上の遅延が発生する可能性）
- billingms 単一インスタンス前提（IT8 でクラスタ化検討）のため Cache hit rate が高く、bookingms への実 RPC は更に少ない

将来 billingms をクラスタ化（IT8+）したり、Shipper 参照頻度が上がった場合は本 ADR を見直し、`ShipperRegisteredEvent` / `ShipperContractUpdatedEvent` の shared kernel 昇格 + Read Model レプリケーションを再検討する。

### 4. ProcessingGroup 命名（ADR-0014 派生）

billingms で本 ADR が新規追加する `@ProcessingGroup` は以下（IT7 実装完了時点、review H1 修正反映済み）:

| 名前 | 種別 | 役割 | 実装クラス |
| :--- | :--- | :--- | :--- |
| `cross-billing` | cross- prefix | `CargoDeliveredEvent`（shared）を購読 → `CalculateInvoiceCommand` 発火 | `CrossCargoDeliveredEventHandler` |
| `local-billing` | local- prefix | `invoice` / `invoice_line` / `payment` 投影更新 | `InvoiceProjectionsEventHandler` |
| `outbound-billing-notification` | outbound- prefix | `InvoiceIssuedEvent` / `PaymentRecordedEvent`（shared）/ `InvoiceOverdueEvent` → NotificationAcl | `InvoiceNotificationEventHandler` |

> **review H1 教訓（IT7、commit 657e4a5a で反映）**: 設計初期に `outbound-billing-cross` という 4 つ目のグループ
> （`SharedPaymentRecordedEventPublisher` で内部 `PaymentRecordedEvent` を shared 版に変換して再 publish）を導入したが、
> これは ADR-0012 §2 集約発火型違反の「二段イベント」パターン（trackingms `CargoDeliveredEventPublisher` 同型）であった。
> IT7 内対応で内部 event + publisher を廃止し、`Invoice` 集約から直接 shared `PaymentRecordedEvent` を `apply` する設計に統一済み。
> 詳細は ADR-0012 §自己整合チェックリスト C1-C4 / PR1 を参照。

bookingms 側に追加する `CrossBillingPaymentHandler` は `cross-booking-billing` グループ（shared `PaymentRecordedEvent` を購読）。

## 影響

### 適用対象

- **billingms 新設**: `CrossCargoDeliveredEventHandler` / `Invoice` 集約の冪等化 / `RestShipperInfoAcl` (Resilience4j + Caffeine) を実装（IT7 タスク 2.3 / 2.4 / 3.1 / 3.2）
- **bookingms**: `GET /api/v1/shippers/{id}` を新規公開（IT2 で実装済みの ShipperController を確認、未実装ならエンドポイント追加）。さらに `PaymentRecordedEvent` 受信用 `CrossBillingPaymentHandler` を追加（IT7 タスク 4.5）
- **trackingms**: 変更不要（IT7 T2 で `CargoDeliveredEvent` 集約発火型移行は完了済み）

### 追加依存

| 依存 | バージョン | 用途 |
| :--- | :--- | :--- |
| `resilience4j-spring-boot3` | 2.x | Circuit Breaker（`@CircuitBreaker` アノテーション）|
| `caffeine` | 3.x | アプリ層 cache（Spring `@Cacheable` で利用、TTL 5min） |

両者とも `tech_stack.md` 未掲載のため、IT7 で **technical-writer 観点の tech_stack 更新** が必要。

### 受け入れテスト

- `CrossCargoDeliveredEventHandlerTest`: `CargoDeliveredEvent` 購読で `CalculateInvoiceCommand` が発火されることをモックで検証
- `InvoiceAggregateTest` (Axon Test Fixture): `CalculateInvoiceCommand` を 2 回送信しても 2 回目以降は no-op であることを検証（冪等性）
- `RestShipperInfoAclTest`: Caffeine cache hit / CB open 時の fallback / TTL 切れ後の再フェッチ を検証
- 統合テスト: `bookingms` をモック停止した状態で billingms の `FinalizeInvoiceCommand` がフォールバック割引率 0% で完了することを検証

### 既存 ADR との関係

- **ADR-0009 cross-service Saga**: 本 ADR は ADR-0009 の運用規約を billingms に適用
- **ADR-0011 Kafka tracking エラーハンドリング**: 本 ADR の集約内冪等化と ADR-0011 のホワイトリスト方式は直交（成功経路 vs 失敗経路）
- **ADR-0012 cross-service 冪等性**: 本 ADR は ADR-0012 規約 1（集約発火型）+ 規約 2（投影フラグ列 - Invoice の billingStatus が役割）に完全準拠
- **ADR-0014 ProcessingGroup 命名**: 本 ADR で追加する 3 グループはすべて ADR-0014 prefix 規約に準拠

## コンプライアンス

- 新規 ACL を追加する PR では、circuit breaker + cache + fallback の 3 層がそろっているかをレビュー観点に追加
- billingms の `CrossCargoDeliveredEventHandler` のテストで「同じ `CargoDeliveredEvent` を 2 回送って 2 回目が no-op」を必ず検証
- ArchUnit テスト（ADR-0014 と統合）で `@ProcessingGroup("cross-*")` を持つクラスは必ず `interfaces/events/` 配下に配置されることを構造ガード
- `architecture_backend.md` に「Shipper 同期参照パターン」を追加し、将来別コンテキストが Shipper を参照する場合の参考パターンとする

## 備考

- 著者: k2works (IT7 計画時)
- 関連 Issue: take-5 #192 / #193 / #194（IT7 US21 / US22 / US23）
- 関連 ADR: ADR-0009 / ADR-0011 / ADR-0012 / ADR-0014
- 関連コミット: 6837f495（IT7 T2 集約発火型移行による ADR-0012 自己整合回復）
- 将来見直し条件:
  - billingms がクラスタ化される（IT8+）→ ShedLock 等の排他制御と併せて Shipper レプリケーション再検討
  - Shipper 参照頻度が「精算時のみ」を超える（例: フロントが billingms 経由で Shipper を表示）→ Read Model レプリケーションへ移行
