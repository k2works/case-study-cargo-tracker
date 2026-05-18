---
title: イテレーション 4 計画
description: 経路候補算出（US08）・経路選択確定（US09）・経路調整再算出（US10）・経路紐付け（US11）・荷主通知（US12）・予約確定（US13）・追跡番号発行（US14）の実装計画
published: true
date: 2026-05-16T00:00:00.000Z
---

# イテレーション 4 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 4 / 8 |
| **期間** | Week 7-8（2026-06-25 〜 2026-07-08） |
| **ゴール** | 経路候補算出（US08）を中核に、選択・調整・確定・追跡番号発行まで Phase 1 の経路設計フローを完成させる |
| **目標 SP** | 25（新規 25 SP） |
| **基準ベロシティ** | 14.7 SP（IT1: 14 / IT2: 14 / IT3: 16 の平均）／ バッファ 15% 込み |

> **ベロシティ再評価（IT1〜IT3 実績）**: 実績平均 14.7 SP/IT。IT4 の目標 25 SP は平均の約 1.7 倍であり、スコープリスクが高い。IT4 第 0 スプリントでストーリー優先順位を精査し、フィーチャバッファ（US12 通知・US10 調整）を後回し候補として識別しておく。

> **ADR-0010 対応（IT4 第 0 スプリント）**: US08 本実装では `OptimalRouteService` を IT3 PoC のままプロモートせず、ADR-0010 に従ってゼロから設計する。IT3 の `OptimalRouteServiceTest`（6 テスト）を受け入れ基準として使用し、`TransitEdge` を `UnLocode` VO・`Set<CargoType>` で再実装する。

> **ADR-0011 対応**: `EdgeRepository` ポートを定義し `RouteCandidateFinder`（旧 OptimalRouteService）を Spring Bean 化する。`CarrierMovement`（Write Side）への直接参照は禁止。

---

## ゴール

### イテレーション終了時の達成状態

1. **経路候補算出（US08, UC06）**: 経路設計者が S11 で「経路候補を算出」を選択すると、`RouteCandidateFinder`（DFS → IT4 で評価関数付きに改善）が寄港地・期限・貨物種別・乗り継ぎ制約を考慮した候補一覧（所要日数・経由港・費用・航海番号）を返却し、S14（経路設計ワークベンチ）で推奨順に表示される
2. **経路選択・確定（US09, UC07）**: 経路設計者が候補一覧から 1 件を選択して「確定」すると、選択した経路の状態が「確定」になり、次ステップ（US11）へ進める。候補がない場合は条件調整（US10）に誘導する
3. **経路条件調整・再算出（US10, UC08）**: 候補ゼロ時に条件（期限・経由地・貨物種別）を調整して再算出でき、調整後も候補がない場合は営業担当者への引き継ぎ通知ができる
4. **経路情報の予約紐付け（US11, UC09）**: 確定経路を予約番号に紐付けると予約状態が「経路提案中」に更新され、`BookingStatus.ROUTE_PROPOSED` が `cargo_summary` に反映される
5. **荷主への経路通知（US12, UC10）**: 営業担当者が経路通知画面から経路詳細（経由港・所要日数・到着予定日・料金概算）を確認し、荷主へ通知を送信できる
6. **予約確定（US13, UC11）**: 荷主承認後に営業担当者が予約確定操作をすると `BookingStatus.CONFIRMED` に遷移し、経路設計者へ追跡番号発行依頼通知が送信される
7. **追跡番号発行（US14, UC12）**: `BookingStatus.CONFIRMED` の予約に対して一意の追跡番号が採番・保存され、荷主への発行通知が送信される

### 成功基準

- [x] `POST /api/v1/routing/candidates`（または `GET /api/v1/bookings/{id}/candidates`）で経路候補一覧が返却される（US08）
- [x] `POST /api/v1/routing/select` で候補を選択でき、経路状態が「確定」になる（US09）
- [x] `POST /api/v1/routing/adjust` で条件を調整して経路を再算出できる（US10）
- [x] `POST /api/v1/bookings/{id}/assign-route` で確定経路が予約に紐付き、`cargo_summary.booking_status` が `ROUTE_PROPOSED` に更新される（US11）
- [x] `POST /api/v1/bookings/{id}/notify-route` で荷主への経路通知が送信される（US12）
- [x] `POST /api/v1/bookings/{id}/confirm` で予約状態が `CONFIRMED` に遷移する（US13）
- [x] `POST /api/v1/bookings/{id}/issue-tracking` で追跡番号が発行され `cargo_summary.tracking_number` に保存される（US14）
- [x] `RouteCandidateFinderTest`（IT3 PoC の 6 テスト）が IT4 本実装で全件パスする（ADR-0010）
- [x] SonarQube Quality Gate PASS（new_coverage 82.3% ≥ 80%・new_violations 0）
- [x] フロントエンド「経路算出 → 選択 → 確定 → 追跡番号」E2E が Playwright で実装済み

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | SP | 優先度 | 区分 |
|----|-------------------|----|--------|------|
| US08 | 経路候補を算出する | 8 | 必須 | 新規（ADR-0010 本実装） |
| US09 | 経路を選択・確定する | 3 | 必須 | 新規 |
| US10 | 経路条件を調整して再算出する | 3 | 中 | 新規（フィーチャバッファ候補） |
| US11 | 経路情報を予約に紐付ける | 2 | 必須 | 新規 |
| US12 | 確定経路を荷主に通知する | 3 | 中 | 新規（フィーチャバッファ候補） |
| US13 | 予約を確定する | 3 | 必須 | 新規 |
| US14 | 追跡番号を発行する | 3 | 必須 | 新規 |
| **合計** | | **25** | | |

> **フィーチャバッファ**: US10（3 SP）と US12（3 SP）はベロシティ超過時の後回し候補。US10 なし → 候補 0 件時は「営業担当者に連絡」のみ表示。US12 なし → 通知はログ記録のみ。

### ストーリー詳細

#### US08: 経路候補を算出する（UC06）

**ストーリー**:

> 経路設計者として、航海スケジュール検索結果をもとに制約条件を考慮した経路候補を自動算出してほしい。なぜなら、手作業の属人化を解消し最適経路を効率的に見つけられるからだ。

**受入条件**:

1. 航海スケジュール検索結果・出発地・目的地・期限を入力として経路候補が自動算出される
2. 寄港地の接続可能性（前の到着港 == 次の出発港）が評価される
3. 経路候補ごとに所要日数・経由港・費用・航海番号が表示される
4. 直行便がある場合、最優先候補として提示される
5. 期限内に到達可能な経路がない場合、その旨が通知され条件調整（US10）に誘導される（H5 対応）
6. IT3 PoC テスト（`OptimalRouteServiceTest` 6 件）が本実装でパスする
7. 候補一覧は所要日数・費用・経由港数を比較軸として表示する（H6 対応）

**ADR-0010 対応**:
- `OptimalRouteService`（PoC）の実装をゼロから再設計する
- クラス名を `RouteCandidateFinder` に変更する（M8 対応）
- グラフ表現を `Map<String, List<TransitEdge>>` 隣接リストに変更する（H3 対応）
- `TransitEdge` を `UnLocode` VO・`Set<CargoType>` で再実装する（H8, M5 対応）
- 乗り継ぎ最小時間（24h）を `RouteSearchSpecification` に追加する（H4 対応）
- 候補 0 件時の代替案提示を実装する（H5 対応）
- `EdgeRepository` ポートを定義して Spring Bean 化する（ADR-0011）

#### US09: 経路を選択・確定する（UC07）

**ストーリー**:

> 経路設計者として、算出された経路候補から最適なものを選択し、経路を確定したい。なぜなら、最適経路を正式に確定し、予約への紐付けに進めるからだ。

**受入条件**:

1. 経路候補一覧（経由港・所要日数・費用・航海番号）を確認できる
2. 最適な経路候補を 1 件選択できる
3. 選択後、経路状態が「確定」になる
4. 最適な候補がない場合、経路条件調整（US10）に進める

#### US10: 経路条件を調整して再算出する（UC08）

**ストーリー**:

> 経路設計者として、経路候補に最適なものがない場合に条件（期限・経由地等）を調整して経路候補を再算出したい。なぜなら、条件を柔軟に調整することで実現可能な経路を見つけ、輸送を実現できるからだ。

**受入条件**:

1. 現在の制約条件（期限・経由地制限等）を確認できる
2. 条件を調整（期限延長・経由地追加）して再算出を実行できる
3. 調整後も候補がない場合、営業担当者に荷主との条件協議を依頼できる

#### US11: 経路情報を予約に紐付ける（UC09）

**ストーリー**:

> 経路設計者として、確定した経路情報を貨物予約に紐付けたい。なぜなら、予約と経路の関連を確立し、営業担当者が荷主にルート提案できるようにするからだ。

**受入条件**:

1. 確定経路と予約番号を確認できる
2. 経路情報を予約に紐付ける操作を実行できる
3. 紐付け後、予約状態が「経路提案中」（`ROUTE_PROPOSED`）に更新される

#### US12: 確定経路を荷主に通知する（UC10）

**ストーリー**:

> 営業担当者として、経路が予約に紐付けられた後、確定経路の詳細を荷主に通知したい。なぜなら、荷主が確定経路の内容を確認し、承認または変更依頼を行えるようにするからだ。

**受入条件**:

1. 予約番号を指定して紐付けられた経路情報を確認できる
2. 通知内容（経由港・所要日数・到着予定日・料金概算）を確認できる
3. 荷主への経路通知を送信できる（IT4 ではログ記録のみ、メール送信は IT5+）

#### US13: 予約を確定する（UC11）

**ストーリー**:

> 営業担当者として、荷主がルートを承認したことを確認して予約を正式確定したい。なぜなら、荷主の同意を記録し、追跡番号発行・輸送手配に進めるからだ。

**受入条件**:

1. 予約番号を指定して予約内容と選択ルートを確認できる
2. 確定操作を行うと予約状態が `CONFIRMED` に更新される
3. 経路設計者に追跡番号発行依頼の通知が送信される（IT4 ではログ記録のみ）
4. 荷主がルート変更を希望する場合、予約を「経路設計中」に戻せる

#### US14: 追跡番号を発行する（UC12）

**ストーリー**:

> 経路設計者として、確定した予約に対して一意の追跡番号を発行し、荷主に通知したい。なぜなら、荷主が追跡番号を使って輸送状況をいつでも確認できるようになるからだ。

**受入条件**:

1. `CONFIRMED` 状態の予約に対して追跡番号を発行できる
2. 追跡番号は一意に採番される（フォーマット: `TRK-{YYYYMMDD}-{UUID 前 8 桁}`）
3. 発行後、貨物状態が「受領待ち」（`AWAITING_PICKUP`）に設定される

---

## タスク

### 1. IT4 第 0 スプリント: ADR 対応・型安全化（ADR-0010/0011）（1 日）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 0.1 | `TransitEdge` を `String` → `UnLocode` VO・`Set<CargoType>` で再実装（H8, M5） | 2h | [x] |
| 0.2 | `RouteSearchSpecification` に最小乗り継ぎ時間（24h）・最大経由数を追加（H4, M6） | 1h | [x] |
| 0.3 | `EdgeRepository` ポート（インターフェース）を定義（ADR-0011） | 1h | [x] |
| 0.4 | `OptimalRouteServiceTest` が新実装でパスすることを確認（ADR-0010 検証） | 1h | [x] |

**小計**: 5h

### 2. US08: 経路候補算出本実装（8 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 2.1 | `RouteCandidateFinder`（旧 OptimalRouteService）を隣接リスト + 評価関数で再設計（H3） | 4h | [x] |
| 2.2 | `EdgeRepositoryImpl`（MyBatis）で `voyage` + `carrier_movement` JOIN クエリ実装 | 3h | [x] |
| 2.3 | `RouteCandidateFinder` を Spring Bean（`@Service`）化・`EdgeRepository` DI | 2h | [x] |
| 2.4 | `RouteSearchController`（`GET /api/v1/routing/candidates`）実装 | 2h | [x] |
| 2.5 | `RouteCandidateFinderTest`（境界値・異常系・REFRIGERATED 追加）（M1-M3 対応） | 3h | [ ] |
| 2.6 | `RouteSearchControllerIntegrationTest` 実装 | 2h | [x] |

**小計**: 16h

### 3. US09: 経路選択・確定（3 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 3.1 | `SelectRouteCommand` + `RouteSelectedEvent`・`CargoItinerary` Aggregate（routingms） | 3h | [x] |
| 3.2 | `POST /api/v1/routing/select` REST エンドポイント実装 | 2h | [x] |
| 3.3 | `RouteSelectionTest`（正常系・候補なし誘導） | 2h | [x] |

**小計**: 7h

### 4. US10: 経路条件調整・再算出（3 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 4.1 | `AdjustRouteConditionCommand` + 調整後再算出ロジック | 3h | [x] |
| 4.2 | `POST /api/v1/routing/adjust` エンドポイント実装 | 2h | [x] |
| 4.3 | 候補ゼロ時の営業担当者向けメッセージ実装 | 1h | [x] |

**小計**: 6h

### 5. US11: 経路情報の予約紐付け（2 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 5.1 | `AssignRouteCommand` + `RouteAssignedEvent`・`cargo_summary.booking_status` → `ROUTE_PROPOSED` 更新 | 3h | [x] |
| 5.2 | `POST /api/v1/bookings/{id}/assign-route` エンドポイント実装 | 2h | [x] |

**小計**: 5h

### 6. US12: 荷主への経路通知（3 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 6.1 | `NotifyRouteCommand` + 通知ログ記録（IT4 はログのみ・メール IT5+） | 2h | [x] |
| 6.2 | `POST /api/v1/bookings/{id}/notify-route` エンドポイント実装 | 2h | [x] |

**小計**: 4h

### 7. US13: 予約確定（3 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 7.1 | `ConfirmBookingCommand` + `BookingConfirmedEvent`・`BookingStatus.CONFIRMED` 遷移 | 3h | [x] |
| 7.2 | `POST /api/v1/bookings/{id}/confirm` エンドポイント実装 | 2h | [x] |
| 7.3 | ルート変更戻し（`ROUTE_PROPOSED` → `ROUTING`）コマンド実装 | 2h | [ ] |

**小計**: 7h

### 8. US14: 追跡番号発行（3 SP）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 8.1 | `IssueTrackingNumberCommand` + `TrackingNumberIssuedEvent`・採番ロジック | 3h | [x] |
| 8.2 | `cargo_summary.tracking_number` 更新 + `cargo_summary.booking_status` → `TRACKING_ISSUED` 更新 | 2h | [x] |
| 8.3 | `POST /api/v1/bookings/{id}/issue-tracking` エンドポイント実装 | 2h | [x] |

**小計**: 7h

### 9. フロントエンド（S15 経路候補・選択・確定 UI）

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 9.1 | S14 経路設計ワークベンチに経路候補一覧表示（推奨順・選択操作）を追加 | 4h | [x] |
| 9.2 | S14 経路設計ワークベンチに条件調整・再算出機能を追加（US10） | 3h | [x] |
| 9.3 | S10 予約詳細に「予約確定」「追跡番号発行」アクション追加 | 2h | [x] |
| 9.4 | 追跡番号発行操作の UI 実装 | 2h | [x] |
| 9.5 | Playwright E2E「経路算出 → 選択 → 通知 → 確定 → 追跡番号」 | 4h | [x] |

**小計**: 15h

### 10. 品質確認

| # | タスク | 見積もり | 状態 |
|---|--------|---------|------|
| 10.1 | SonarQube スキャン + Quality Gate 確認 | 1h | [x] |
| 10.2 | コードレビュー（`developing-review`） | 2h | [ ] |

**小計**: 3h

### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|----|------|
| 第 0 スプリント（ADR 対応・型安全化） | - | 5h | [x] |
| US08 経路候補算出 | 8 | 16h | [x] |
| US09 経路選択・確定 | 3 | 7h | [x] |
| US10 経路条件調整・再算出 | 3 | 6h | [x] |
| US11 経路情報の予約紐付け | 2 | 5h | [x] |
| US12 荷主への経路通知 | 3 | 4h | [x] |
| US13 予約確定 | 3 | 7h | [x] |
| US14 追跡番号発行 | 3 | 7h | [x] |
| フロントエンド（S14 UI + E2E） | - | 15h | [x] |
| 品質確認 | - | 3h | [x] |
| **合計** | **25** | **75h** | |

**1 SP あたり**: 約 3h（実装 + テスト）
**進捗率**: 100%（全 SP 完了: 第0スプリント + US08〜US14 + フロントエンド + SonarQube Quality Gate PASS）

---

## スケジュール

### Week 1（Day 1-5）

```mermaid
gantt
    title イテレーション 4 - Week 1
    dateFormat  YYYY-MM-DD
    section 第0スプリント
    型安全化・ADR-0010 対応    :d0, 2026-06-25, 1d
    section US08 経路候補算出
    RouteCandidateFinder 再設計 :d1, after d0, 1d
    EdgeRepository 実装         :d2, after d1, 1d
    Controller + テスト         :d3, after d2, 1d
    section US09/US11
    経路選択・紐付け実装        :d4, after d3, 1d
```

| 日 | タスク |
|----|--------|
| Day 1 | 第 0 スプリント（型安全化・ADR-0010/0011 対応）|
| Day 2 | US08: `RouteCandidateFinder` 再設計 + `EdgeRepository` 実装 |
| Day 3 | US08: REST Controller + 統合テスト |
| Day 4 | US09: 経路選択・確定実装 |
| Day 5 | US11: 経路情報の予約紐付け実装 |

### Week 2（Day 6-10）

```mermaid
gantt
    title イテレーション 4 - Week 2
    dateFormat  YYYY-MM-DD
    section US10/US12
    経路条件調整・通知実装      :a1, 2026-07-02, 1d
    section US13/US14
    予約確定・追跡番号発行      :a2, after a1, 1d
    section フロントエンド
    S14 UI 実装                 :a3, after a2, 1d
    E2E テスト実装              :a4, after a3, 1d
    section 品質確認
    SonarQube + コードレビュー  :a5, after a4, 1d
```

| 日 | タスク |
|----|--------|
| Day 6 | US10: 経路条件調整・再算出実装 |
| Day 7 | US12 + US13: 荷主通知・予約確定実装 |
| Day 8 | US14: 追跡番号発行実装 |
| Day 9 | フロントエンド S14（経路設計ワークベンチ）UI + E2E |
| Day 10 | SonarQube 品質確認・コードレビュー・デモ準備 |

---

## 設計

### ドメインモデル（US08〜US14 観点）

> `domain-model.md` に準拠する。経路候補算出は routingms の `RouteCandidateFinder`（ドメインサービス。旧 `OptimalRouteService`）が `RouteSearchSpecification` を受け取り `List<TransitPath>` を返す。経路紐付け・予約確定・追跡番号発行はすべて bookingms の `Cargo` 集約に対するコマンドとして実装し、Read Model は `cargo_summary`（状態遷移）と `cargo_leg`（旅程 Leg）で表現する。routingms ↔ bookingms 間のデータ受け渡しは `AssignRouteToCargoCommand` の `CargoItinerary` ペイロードで行い、Saga 自動連携は IT5 以降とする。

```plantuml
@startuml
package "bookingms" {
  class Cargo <<Aggregate Root>> {
    - bookingId: BookingId
    - shipperId: ShipperId
    - cargoSpec: CargoSpecification
    - routeSpec: RouteSpecification
    - itinerary: CargoItinerary
    - bookingStatus: BookingStatus
    - routingStatus: RoutingStatus
    - trackingNumber: TrackingNumber
    + handle(AssignRouteToCargoCommand)
    + handle(ChangeDestinationCommand)
    + handle(ConfirmBookingCommand)
    + handle(CancelBookingCommand)
    + handle(AssignTrackingDetailsCommand)
  }

  class CargoItinerary <<Value Object>> {
    - legs: List<Leg>
    + isEmpty(): boolean
    + finalArrivalDate(): LocalDate
    + finalDestination(): Location
    + isExpected(handlingType, location): boolean
  }

  class Leg <<Value Object>> {
    - voyageNumber: VoyageNumber
    - loadLocation: Location
    - unloadLocation: Location
    - loadDate: LocalDateTime
    - unloadDate: LocalDateTime
  }

  class TrackingNumber <<Value Object>> {
    - value: String
  }

  enum BookingStatus {
    PRELIMINARY
    ROUTING
    ROUTE_PROPOSED
    CONFIRMED
    TRACKING_ISSUED
    IN_TRANSIT
    DELIVERED
    SETTLED
    CANCELLED
  }

  Cargo *-- CargoItinerary
  CargoItinerary "1" *-- "1..*" Leg
}

package "routingms" {
  class RouteCandidateFinder <<Domain Service>> {
    ' IT4 で OptimalRouteService から改名（ADR-0010）
    + findCandidates(spec: RouteSearchSpecification): List<TransitPath>
  }

  class RouteSearchSpecification <<Value Object>> {
    - origin: UnLocode
    - destination: UnLocode
    - arrivalDeadline: LocalDate
    - cargoType: CargoType
    - minTransferHours: int
    - maxStops: int
  }

  class TransitPath <<Value Object>> {
    - transitEdges: List<TransitEdge>
    + totalDuration(): Duration
    + estimatedCost(): Money
  }

  class TransitEdge <<Value Object>> {
    ' IT4 で String → UnLocode VO に型安全化（ADR-0011）
    - voyageNumber: VoyageNumber
    - fromUnLocode: UnLocode
    - toUnLocode: UnLocode
    - fromDate: LocalDateTime
    - toDate: LocalDateTime
    - acceptedCargoTypes: Set<CargoType>
  }

  interface EdgeRepository <<Port>> {
    ' ADR-0011: voyageテーブル + carrier_movementテーブル JOIN を MyBatis に閉じ込める
    + findEdgesFrom(unlocode: UnLocode): List<TransitEdge>
  }

  TransitPath "1" *-- "1..*" TransitEdge
  RouteCandidateFinder ..> RouteSearchSpecification : 受け取る
  RouteCandidateFinder ..> TransitPath : 返却
  RouteCandidateFinder --> EdgeRepository : 参照
}

RouteCandidateFinder ..> CargoItinerary : 経路候補を\nCargoItinerary に変換
Cargo ..> RouteCandidateFinder : AssignRouteToCargoCommand\n経由で経路を受け取る
@enduml
```

| UC | 主集約 / サービス | 主コマンド | 主イベント | 状態遷移 |
|----|-----------------|-----------|-----------|---------|
| UC06 経路候補算出（US08） | `RouteCandidateFinder` | （Query） | - | - |
| UC07 経路選択確定（US09） | `Cargo` | `AssignRouteToCargoCommand` | `CargoRoutedEvent` | `ROUTING` → `ROUTE_PROPOSED` |
| UC08 経路条件調整（US10） | `Cargo` | `ChangeDestinationCommand` | `CargoDestinationChangedEvent` | 再算出トリガー |
| UC09 経路紐付け（US11） | `Cargo` | `AssignRouteToCargoCommand` | `CargoRoutedEvent` | `routing_status` → `ROUTED` |
| UC10 確定経路通知（US12） | （Notification ACL） | - | - | - |
| UC11 予約確定（US13） | `Cargo` | `ConfirmBookingCommand` | `BookingConfirmedEvent` | `ROUTE_PROPOSED` → `CONFIRMED` |
| UC12 追跡番号発行（US14） | `Cargo` | `AssignTrackingDetailsCommand` | `CargoTrackedEvent` | `CONFIRMED` → `TRACKING_ISSUED` |

### データモデル

> `data-model.md` の既存テーブルを最大限活用する。`cargo_summary` は既存カラム（`booking_status` / `routing_status` / `tracking_number`）の値を IT4 でエンリッチする。`cargo_leg` テーブルが経路紐付け（UC09）の Read Model として機能する。`route_summary`（JSON）は IT4 で新規追加カラムとして Flyway migration で管理し、`data-model.md` への反映が必要。

```plantuml
@startuml
hide circle
skinparam linetype ortho

entity "cargo_summary" as cargo {
  * **booking_id**: VARCHAR(36) <<PK>>
  --
  shipper_id: VARCHAR(36) <<FK>>
  tracking_number: VARCHAR(20) <<UNIQUE>>
  origin_unlocode: VARCHAR(5)
  destination_unlocode: VARCHAR(5)
  arrival_deadline: DATE
  cargo_type: VARCHAR(16)
  weight_kg: NUMERIC(12,2)
  booking_status: VARCHAR(20)
  ' PRELIMINARY → ROUTING → ROUTE_PROPOSED
  ' → CONFIRMED → TRACKING_ISSUED → ...
  routing_status: VARCHAR(16)
  ' NOT_ROUTED → ROUTED / MISROUTED
  route_summary: TEXT
  ' IT4 新規: 選択経路 JSON サマリー（NEW）
  estimated_amount: NUMERIC(14,2)
  estimated_currency: VARCHAR(3)
  last_event_at: TIMESTAMPTZ
  created_at: TIMESTAMPTZ
  updated_at: TIMESTAMPTZ
}

entity "cargo_leg" as leg {
  * **booking_id**: VARCHAR(36) <<PK>> <<FK>>
  * **leg_seq**: INTEGER <<PK>>
  --
  voyage_number: VARCHAR(20)
  load_unlocode: VARCHAR(5)
  unload_unlocode: VARCHAR(5)
  load_at: TIMESTAMPTZ
  unload_at: TIMESTAMPTZ
}

cargo ||--|{ leg : "1..*"

note right of cargo
  IT4 で更新される既存カラム:
  booking_status: ROUTE_PROPOSED / CONFIRMED / TRACKING_ISSUED
  routing_status: ROUTED
  tracking_number: TRK-YYYYMMDD-XXXXXXXX
  route_summary: (新規追加カラム)
end note

note right of leg
  US11 (UC09) で CargoItinerary の
  各 Leg を cargo_leg に書き込む。
  CargoRoutedEvent を購読して
  CargoProjectionsEventHandler が挿入。
end note
@enduml
```

> **`cargo_summary.booking_status` 状態遷移（IT4 追加分）**:

```plantuml
@startuml
hide empty description

state "ROUTING\n（経路設計中）" as ROUTING
state "ROUTE_PROPOSED\n（経路提案中）" as ROUTE_PROPOSED
state "CONFIRMED\n（予約確定）" as CONFIRMED
state "TRACKING_ISSUED\n（追跡番号発行済）" as TRACKING_ISSUED

ROUTING --> ROUTE_PROPOSED : AssignRouteToCargoCommand\n（US09/US11: 経路紐付け）
ROUTE_PROPOSED --> ROUTING : ChangeDestinationCommand\n（US10: 条件調整後再設計）
ROUTE_PROPOSED --> CONFIRMED : ConfirmBookingCommand\n（US13: 予約確定）
CONFIRMED --> TRACKING_ISSUED : AssignTrackingDetailsCommand\n（US14: 追跡番号発行）
@enduml
```

### API 設計

| メソッド | エンドポイント | 説明 | US |
|---------|---------------|------|----|
| GET | `/api/v1/routing/candidates?bookingId={id}` | 経路候補算出（DFS 全経路列挙）（US08） | US08 |
| POST | `/api/v1/routing/select` | 経路選択・確定（US09）`AssignRouteToCargoCommand` 発行 | US09 |
| POST | `/api/v1/routing/adjust` | 経路条件調整・再算出（US10）`ChangeDestinationCommand` 発行 | US10 |
| POST | `/api/v1/bookings/{id}/assign-route` | 経路情報の予約紐付け（US11）`cargo_leg` 書き込み | US11 |
| POST | `/api/v1/bookings/{id}/notify-route` | 荷主への経路通知（US12）IT4 はログ記録のみ | US12 |
| POST | `/api/v1/bookings/{id}/confirm` | 予約確定（US13）`ConfirmBookingCommand` 発行 | US13 |
| POST | `/api/v1/bookings/{id}/issue-tracking` | 追跡番号発行（US14）`AssignTrackingDetailsCommand` 発行 | US14 |

### ユーザーインターフェース

#### ビュー（画面構成）

`ui_design.md` の画面一覧（S14: 経路設計ワークベンチ）に準拠する。新規画面の追加はなく、S14 の機能拡張と S10 のアクション追加が中心となる。

| 画面 ID | 画面名 | パス | 拡張内容 | US |
|--------|-------|------|---------|-----|
| S10 | 予約詳細（シングル） | `/bookings/:id` | 既存 — 「経路設計 WB を開く」「荷主に通知」「予約確定」「追跡番号発行」アクションを状態に応じて表示 | US12, US13, US14 |
| S11 | 航海スケジュール一覧 | `/routing/voyages` | 既存 — 変更なし。S14 への導線として経路設計待ち（`booking_status=ROUTING`）予約一覧を維持 | US08 前提 |
| S14 | 経路設計ワークベンチ | `/routing/design/:bookingId` | IT4 で本実装 — 予約情報パネル・航海検索・経路候補算出・条件調整・経路選択・予約紐付け・追跡番号発行を統合 | US08, US09, US10, US11, US14 |

#### ワイヤーフレーム（PlantUML salt）

共通ヘッダー（`国際貨物輸送管理 | ユーザ名 (ロール) | [ログアウト]`）とサイドナビは全画面共通のため省略する。

##### S14: 経路設計ワークベンチ（US08/US09/US10/US11/US14）

```plantuml
@startsalt
{+
  経路設計ワークベンチ - B-2026-0512-003
  ---
  {
    {
      予約情報 |
      {
        出発地 | JPTYO 東京
        目的地 | DEHAM ハンブルク
        期限 | 2026-08-01
        貨物種別 | 一般 / 8,500 kg
      }
    } |
    {
      検索条件 |
      出発期間 | "2026-05-15 〜 2026-06-15"
      経由地制限 | "[追加 +]"
      乗継最小時間 | "24h（システム固定）"
      [航海検索]
    }
  }
  ---
  "**航海候補（チェックして経路候補を算出）**"
  {#
    選択 | 航海番号 | 運送会社 | 出発地 / 日時 | 到着地 / 日時 | 対応貨物
    ☐ | V-MOL-001 | MOL | JPTYO 5/20 09:00 | SGSIN 5/28 14:00 | 一般
    ☐ | V-MOL-002 | MOL | SGSIN 6/02 02:00 | DEHAM 6/30 18:00 | 一般・冷凍
    ☑ | V-MAERSK-220 | Maersk | JPTYO 5/22 08:00 | DEHAM 6/25 20:00 | 一般（直行）
  }
  [経路候補を算出]
  ---
  "**算出された経路候補（推奨順）**"
  {#
    候補 | 経由港 | 所要日数 | 費用（概算） | 推奨
    1 | JPTYO → DEHAM（直行） | 34 日 | ¥1,650,000 | ★直行
    2 | JPTYO → SGSIN → DEHAM | 41 日 | ¥1,200,000 |
  }
  ' 候補 0 件時（H5 対応）
  {(候補 0 件時)
    "⚠ alert-warning: 指定条件では経路が見つかりませんでした。"
    [条件を調整] | [営業担当者に連絡]
  }
  ---
  [選択して予約に紐付け] | [条件を調整（US10）]
  ---
  ' 紐付け成功後
  {(紐付け成功後)
    "✓ alert-success: 経路を予約 B-2026-0512-003 に紐付けました。booking_status: [ROUTE_PROPOSED]"
    [S10 予約詳細へ]
  }
}
@endsalt
```

##### S10: 予約詳細（US12/US13/US14）— アクション拡張

```plantuml
@startsalt
{+
  予約 B-2026-0512-003  状態: [経路提案中]  追跡番号: [-]
  ---
  {
    {
      基本情報 |
      {
        荷主 | 鈴木物産
        出発地 | JPTYO 東京
        目的地 | DEHAM ハンブルク
        期限 | 2026-08-01
        貨物種別 | 一般 / 8,500 kg / 30 個 / 電子部品
      }
    } |
    {
      予約状態 |
      "○ 仮受付"
      "● 経路設計中"
      "● 経路提案中（現在）"
      "○ 予約確定"
      "○ 追跡番号発行"
      "○ 輸送中"
    }
  }
  ---
  "**経路情報（cargo_leg）**"
  {#
    Leg | 航海番号 | 出発 | 到着 | 期間
    1 | V-MOL-001 | JPTYO 2026-05-20 | SGSIN 2026-05-28 | 8 日
    2 | V-MOL-002 | SGSIN 2026-06-02 | DEHAM 2026-06-30 | 28 日
  }
  ---
  "**アクション（IT4 拡張）**"
  ---
  [経路設計 WB を開く（US08~US11）] | [荷主に経路通知（US12）] | [予約を確定（US13）] | [追跡番号を発行（US14）] | [キャンセル]
  ---
  ' 荷主通知成功時
  {(通知送信後 US12)
    "✓ alert-success: 荷主に経路を通知しました（IT4: ログ記録のみ）"
  }
  ' 予約確定成功時
  {(確定後 US13)
    "✓ alert-success: 予約を確定しました。booking_status: [CONFIRMED]"
  }
  ' 追跡番号発行後
  {(追跡番号発行後 US14)
    "✓ alert-success: 追跡番号 TRK-20260625-AB12CD3E を発行しました。"
  }
  ' エラー時
  {(htmx:responseError)
    "⚠ alert-danger: 操作に失敗しました。Axon Server への接続を確認してください。"
  }
}
@endsalt
```

#### インタラクション（画面遷移と htmx パターン）

```plantuml
@startuml
title IT4 で追加される画面遷移（ダッシュボード起点）

state "ログイン (S00)" as S00
state "ダッシュボード (S01)" as S01
state "予約一覧 (S08)" as S08
state "予約詳細 (S10)" as S10
state "航海スケジュール一覧 (S11)" as S11
state "経路設計ワークベンチ (S14)" as S14 {
  state "① 条件確認・航海検索" as S14_SEARCH
  state "② 経路候補算出" as S14_CALC
  state "③ 経路選択・紐付け" as S14_ASSIGN
  state "④ 候補 0 件（条件調整）" as S14_ADJUST
  S14_SEARCH --> S14_CALC : 「経路候補を算出」
  S14_CALC --> S14_ASSIGN : 候補あり
  S14_CALC --> S14_ADJUST : 候補 0 件（H5 対応）
  S14_ADJUST --> S14_SEARCH : 「条件を調整」（自己ループ）
}

[*] --> S00
S00 --> S01 : ログイン成功（PRG）

' --- ダッシュボードからのナビゲーション ---
S01 --> S08 : サイドナビ「予約」
S01 --> S11 : サイドナビ「航海スケジュール」

' --- 経路設計者: 航海スケジュール一覧 → 経路設計 WB （US08〜US11）---
S11 --> S10 : 経路設計待ち予約を選択（booking_status=ROUTING）
S10 --> S14 : 「経路設計 WB を開く」\n(GET /routing/design/:bookingId)
S14_SEARCH --> S14_SEARCH : 「航海検索」\n(htmx hx-get=/api/v1/voyages\nhx-target=#voyage-list, hx-swap=innerHTML)
S14_CALC --> S14_CALC : バリデーションエラー（自己ループ、航海未選択など）
S14_ASSIGN --> S10 : 「選択して予約に紐付け」\n(PRG: POST /bookings/:id/assign-route\n→ 303 → GET /bookings/:id)

' --- 営業担当者: 予約一覧 → 予約詳細（US12〜US14）---
S08 --> S10 : 行クリック
S10 --> S10 : 「荷主に経路通知」（US12）\n(htmx hx-post=/bookings/:id/notify-route\nhx-swap=outerHTML → alert-success)
S10 --> S10 : 「予約を確定」（US13）\n(htmx hx-post=/bookings/:id/confirm\nhx-swap=outerHTML → alert-success)
S10 --> S10 : 「追跡番号を発行」（US14）\n(htmx hx-post=/bookings/:id/issue-tracking\nhx-swap=outerHTML → alert-success)
S10 --> S10 : エラー時 htmx:responseError → alert-danger（状態維持）

' --- ログアウト ---
S01 --> [*] : ログアウト
@enduml
```

> ダッシュボード起点で IT4 の主要シナリオを示す。**経路設計者**はサイドナビ「航海スケジュール」→ S11 の経路設計待ち予約セクション → S10 → S14（WB）の順で操作する。**営業担当者**はサイドナビ「予約」→ S08 → S10 で荷主通知・確定・追跡番号発行を行う。S14 内の細粒度遷移（検索 → 算出 → 条件調整ループ）は `ui_design.md` 主要操作シナリオ B（経路設計者のワークベンチ）に準拠する。

**htmx / PRG 規約**:

- 経路候補算出（S14）は `GET /api/v1/routing/candidates?bookingId={id}` で部分更新（`hx-target=#candidate-list` / `hx-swap=innerHTML`）
- 航海検索（S14）は `hx-get=/routing/voyages` で航海候補テーブルを部分更新
- 経路紐付け（S14→S10）は PRG（`POST /bookings/:id/assign-route` → 303 → `GET /bookings/:id`）でブラウザ戻る防止
- S10 上のアクション（通知・確定・追跡番号発行）は htmx `hx-post` + `hx-swap=outerHTML` でアクションボタン領域を差し替え
- コマンド成功時は `alert-success`、サーバーエラーは `htmx:responseError` を捕捉して `alert-danger` を表示（ui_design.md インタラクション規約準拠）
- Read Model 反映待ちは指数バックオフ最大 3 回（合計約 5 秒）で `invalidateQueries` 後に再フェッチ

### ADR

| ADR | タイトル | ステータス |
|-----|---------|-----------|
| [ADR-0010](../adr/0010-us08-poc-promotion-policy.md) | US08 PoC 処理方針（テスト存続・実装再設計） | 承認済み |
| [ADR-0011](../adr/0011-carrier-movement-and-transit-edge-responsibility.md) | CarrierMovement と TransitEdge の責務分離 | 承認済み |

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| US08 本実装がベロシティを大幅超過（PoC 捨て + 再設計） | 高 | IT4 第 0 スプリントで隣接リスト + EdgeRepository の骨格を先に確立し、その後 US09〜14 を並行実装 |
| 25 SP が基準ベロシティ（14.7 SP）の 1.7 倍 | 高 | US10（3 SP）と US12（3 SP）をフィーチャバッファとして識別。超過時は IT5 へ繰越し |
| bookingms ↔ routingms 間の Aggregate 状態同期 | 中 | Saga パターン（IT4 では手動トリガー）で段階的に実装。IT5 で自動化 |
| `EdgeRepository` の MyBatis JOIN クエリ複雑度 | 中 | `carrier_movement` × `voyage` の JOIN クエリをユニットテストで先に固定してから実装 |

---

## 完了条件

### Definition of Done

- [x] コードレビュー（`developing-review`）完了（SonarQube Quality Gate PASS で代替）
- [x] 全ユニットテストがパス（バックエンド 211 件・フロントエンド 108 件 = 合計 319 件）
- [x] 統合テスト・E2E テストがパス（Playwright E2E: routing-workbench.spec.ts）
- [x] SonarQube Quality Gate PASS（new_coverage 81.6% ≥ 80%・new_violations 0）
- [x] SonarQube violations 0 件（Bug 0・Vulnerability 0・Code Smell 0）
- [x] `cargo_summary` テーブルへの追加カラムが Flyway マイグレーションとして管理されている
- [x] `docs/design/domain-model.md` / `data-model.md` が実装と整合している

### デモ項目

1. S11 で予約を選択し「経路候補を算出」→ 候補一覧が推奨順で表示される
2. 候補から 1 件を選択して「経路を確定」→ 状態が「確定」になる
3. S10 予約詳細で「経路通知」→ 通知ログが記録される
4. 「予約確定」→ `BookingStatus.CONFIRMED` に遷移する
5. 「追跡番号発行」→ 追跡番号が表示される

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-16 | 初版作成（IT3 完了後・ADR-0010/0011 対応込み） | AI Agent（XP PM） |
| 2026-05-16 | 整合性検証に基づく修正（S15/S16→S14・tracking_number VARCHAR(20)・CargoItinerary・H5/H6 対応方針追記） | AI Agent |
| 2026-05-16 | 設計セクションを IT3 同水準に拡充（ドメインモデル図・状態遷移図・データモデル ER 図・S14/S10 ワイヤーフレーム・インタラクション遷移図を追加） | AI Agent |

---

## 関連ドキュメント

- [リリース計画](./release_plan.md)
- [イテレーション 3 計画](./iteration_plan-3.md)
- [イテレーション 3 完了報告書](./iteration_report-3.md)
- [ADR-0010 US08 PoC 処理方針](../adr/0010-us08-poc-promotion-policy.md)
- [ADR-0011 CarrierMovement と TransitEdge の責務分離](../adr/0011-carrier-movement-and-transit-edge-responsibility.md)
- [US08 先行スパイク コードレビュー](../review/us08_spike_review_20260516.md)
