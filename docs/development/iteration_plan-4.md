# イテレーション 4 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 4 |
| **期間** | 2026-07-02 〜 2026-07-15（2 週間） |
| **ゴール** | 経路設計者が経路設計待ちリストから経路候補を算出・選択・確定し、確定経路を予約に紐付けて荷主へ通知する。Phase 1 完了・Release 1.0 MVP を達成する。 |
| **目標 SP** | 11（コミット）＋ US10 2 SP（ストレッチ） |

---

## ゴール

### イテレーション終了時の達成状態

1. **経路候補算出（US08）**: 経路設計者が経路設計待ちリスト（route_design_request）から、航海スケジュールと制約条件をもとに経路候補を自動算出し、推奨順に確認できる。
2. **経路の選択・確定（US09）**: 算出候補から最適な 1 件を選択し、経路を確定できる。
3. **経路の予約紐付け（US11）**: 確定経路を予約に紐付け、bookingms の予約状態が cross-service で「経路提案中」に更新される（routingms → bookingms の逆方向 cross-service）。
4. **荷主通知（US12）**: 営業担当者が確定経路の詳細を荷主に通知し、通知記録が残る。
5. **経路設計ワークベンチ（S14）**: 経路設計者ロールのナビゲーションから待ちリスト→候補算出→選択→紐付けの一連の業務を画面上で完結できる（レビュー H3 対応）。

### 成功基準

- [x] US08: 経路設計待ちリストから経路候補が推奨順に算出・表示される（直行便優先、期限内不可時は通知）
- [x] US09: 経路候補から 1 件選択して確定でき、状態が「確定」になる
- [x] US11: 確定経路を予約に紐付けると、bookingms の予約状態が Kafka 経由で「経路提案中」に更新される
- [x] US12: 確定経路を荷主に通知でき、通知記録が登録される
- [x] 経路設計者ロールのナビゲーションに経路設計ワークベンチ（S14）の導線がある
- [x] cross-service（routingms → bookingms）のイベント駆動 E2E がパス（retro Try T2）※ Testcontainers Kafka 統合テスト（`RouteConfirmedKafkaIntegrationTest`）で結合検証。Playwright spec は env-gated（ライブ実行は残課題）
- [x] テストカバレッジ（新規コード）80% 以上（routingms 91.4% / bookingms 84.4%）※ SonarQube ライブスキャンは JaCoCo を代理指標とする（残課題）
- [x] Release 1.0 MVP の開発完了条件（全ユニット・統合テストパス）を満たす ※ Heroku デプロイ・Aiven 接続は運用フェーズの残課題

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | SP | 優先度 |
|----|-------------------|----|----|
| US08 | 経路候補を算出する | 5 | 必須 |
| US09 | 経路を選択・確定する | 2 | 必須 |
| US11 | 経路情報を予約に紐付ける | 2 | 必須 |
| US12 | 確定経路を荷主に通知する | 2 | 必須 |
| US10 | 経路条件を調整して再算出する（ストレッチ） | 2 | 必須（バッファ消費で後回し可） |
| **合計（コミット）** | | **11** | |

> **実装順序**: US08（候補算出 + ワークベンチ）→ US09（選択確定）→ US11（cross-service 紐付け）→ US12（荷主通知）。US10 は US08 のアルゴリズムを再利用する増分のため、コミット分が片付き次第ストレッチで着手し、達成すれば Phase 1 を完全完了する。

### ストーリー詳細

#### US08: 経路候補を算出する

**ストーリー**:
> 経路設計者として、航海スケジュール検索結果をもとに制約条件を考慮した経路候補を自動算出してほしい。なぜなら、手作業の属人化を解消し、制約条件を漏れなく考慮した最適経路を効率的に見つけられるからだ。

**受入条件**:

1. 航海スケジュール検索結果と出発地・目的地・期限を入力として経路候補が自動算出される
2. 寄港地の接続可能性が評価される
3. 経路候補ごとに所要日数・経由港・費用・航海番号が表示される
4. 経路候補が推奨順に並べられて提示される
5. 直行便がある場合、最優先候補として提示される
6. 期限内に到達可能な経路がない場合、その旨が通知され条件調整が促される

#### US09: 経路を選択・確定する

**ストーリー**:
> 経路設計者として、算出された経路候補から最適なものを選択し経路を確定したい。なぜなら、最適経路を正式に確定し予約への紐付けに進めるからだ。

**受入条件**:

1. 経路候補一覧（経由港・所要日数・費用・航海番号）を確認できる
2. 最適な経路候補を 1 件選択できる
3. 選択後、経路状態が「確定」になる
4. 最適な候補がない場合、経路条件調整（US10）に進める

#### US11: 経路情報を予約に紐付ける

**ストーリー**:
> 経路設計者として、確定した経路情報を貨物予約に紐付けたい。なぜなら、予約と経路の関連を確立し営業担当者が荷主にルート提案できるようにするからだ。

**受入条件**:

1. 確定経路と予約番号を確認できる
2. 経路情報を予約に紐付ける操作を実行できる
3. 紐付け後、予約状態が「経路提案中」に更新される（routingms → bookingms の cross-service イベント）

#### US12: 確定経路を荷主に通知する

**ストーリー**:
> 営業担当者として、経路が予約に紐付けられた後、確定経路の詳細を荷主に通知したい。なぜなら、荷主が確定経路の内容を確認し承認または変更依頼を行えるようにするからだ。

**受入条件**:

1. 予約番号を指定して紐付けられた経路情報を確認できる
2. 通知内容（経由港・所要日数・到着予定日・料金概算）を確認できる
3. 荷主への経路通知を送信できる
4. 通知送信記録が登録される

#### US10: 経路条件を調整して再算出する（ストレッチ）

**ストーリー**:
> 経路設計者として、経路候補に最適なものがない場合に条件（期限・経由地等）を調整して経路候補を再算出したい。

**受入条件**:

1. 現在の制約条件を確認できる
2. 条件を調整（期限延長・経由地追加・貨物種別変更等）して再算出を実行できる
3. 調整後の条件で新たな経路候補が算出・提示される
4. 調整後も条件を満たす経路がない場合、営業担当者に条件協議を依頼できる

---

## タスク

### 1. US08 経路候補算出（5 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.1 | routingms: `OptimalRouteService`（経由港接続グラフ探索・直行便優先・推奨順ソート）を TDD で実装 | 8h | - | [x] |
| 1.2 | route_design_request + 航海スケジュールを入力に候補算出する API（`POST /api/v1/routes/{bookingId}/calculate`） | 4h | - | [x] |
| 1.3 | 期限内到達不可時の「候補なし」通知ロジック | 3h | - | [x] |
| 1.4 | フロント: 経路設計ワークベンチ画面（S14）— 待ちリスト表示 + 候補算出トリガー + 候補一覧表示（レビュー H3） | 6h | - | [x] |
| 1.5 | 経路設計者ロールのナビゲーション導線追加（レビュー H3） | 2h | - | [x] |
| 1.6 | 単体・統合テスト（候補算出・接続評価・推奨順・候補なし） | 4h | - | [x] |

**小計**: 27h（理想時間）

### 2. US09 経路選択確定（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | routingms: 経路候補から 1 件選択し確定する状態遷移（Route 集約 or 経路設計状態） | 4h | - | [x] |
| 2.2 | 確定 API + フロント候補選択 UI（ワークベンチ内） | 4h | - | [x] |
| 2.3 | 候補なし時に US10（条件調整）へ誘導 | 2h | - | [x] |
| 2.4 | テスト | 2h | - | [x] |

**小計**: 12h（理想時間）

### 3. US11 経路紐付け（cross-service）（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 3.1 | shared: routingms → bookingms の cross-service イベント `RouteConfirmedEvent`（確定経路＝`CargoItinerary` 相当の `Leg` 列）を定義 | 2h | - | [x] |
| 3.2 | routingms: 経路確定→予約紐付けで `RouteConfirmedEvent` を発行（Kafka publisher） | 3h | - | [x] |
| 3.3 | bookingms: `RouteConfirmedEvent` を tracking 購読する専用ハンドラ（`RouteConfirmedEventHandler`）が `AssignRouteToCargoCommand` を発行。Cargo が `CargoRoutedEvent` を適用して「経路提案中（`ROUTE_PROPOSED`）」へ更新し `cargo_leg` を確定。`BookingSagaManager` は `CargoRoutedEvent` を受けて経路提案中フェーズへ継続する（※実装注参照） | 4h | - | [x] |
| 3.4 | cross-service E2E（イベント駆動、retro Try T2）+ Testcontainers Kafka 統合テスト（`RouteConfirmedKafkaIntegrationTest`）。ライブ E2E で対象予約不在時の `AggregateNotFoundException` をハンドラが冪等スキップする堅牢化も追加 | 3h | - | [x] |

**小計**: 12h（理想時間）

### 4. US12 荷主通知（2 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 4.1 | bookingms: 確定経路の通知内容（経由港・所要日数・到着予定日・料金概算）組み立て + 通知送信コマンド | 4h | - | [x] |
| 4.2 | 通知記録の Read Model + API | 3h | - | [x] |
| 4.3 | フロント: 予約詳細に確定経路表示 + 荷主通知操作 | 3h | - | [x] |
| 4.4 | テスト | 2h | - | [x] |

**小計**: 12h（理想時間）

### 5. 負債返済・品質（SP 外、レビュー / retro Try）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 5.1 | レビュー H4: 見積詳細「予約化」で見積情報を予約フォームにプリセット | 3h | - | [x] |
| 5.2 | Try T1: 認証ヘッダを `shared/api/auth` 経由に統一する規約をレビュー観点テンプレートに追加 | 1h | - | [x] |
| 5.3 | Try T3: JaCoCo はフル `gradle check` で生成し `--tests` 部分実行後は再生成する運用を明文化 | 1h | - | [x] |
| 5.4 | Try T6: 全体品質目標（全体カバレッジ 80%・Code Smell 0）を SonarQube Quality Gate 条件に追加 | 2h | - | [x] |
| 5.5 | Try T5: フロント編集後の E2E 実行チェックリスト（HMR 沈静化 / dev サーバー再起動）整備 | 1h | - | [x] |

**小計**: 8h（理想時間）

### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|----|------|
| US08 経路候補算出 | 5 | 27h | [x] |
| US09 経路選択確定 | 2 | 12h | [x] |
| US11 経路紐付け | 2 | 12h | [x] |
| US12 荷主通知 | 2 | 12h | [x] |
| 負債返済・品質（SP 外） | - | 8h | [x]（H4 プリセット実装 + Try T1/T3/T5/T6 文書・QG 条件化を完了） |
| **合計（コミット）** | **11** | **71h** | |
| US10 経路条件調整（ストレッチ） | 2 | 10h | [x] |

**1 SP あたり**: 約 6.5h（コミット分）
**進捗率**: 100%（11/11 SP コミット分完了）＋ ストレッチ US10（到着期限の上書き再算出）も達成し IT4 計 13 SP。詳細は [iteration_report-4.md](./iteration_report-4.md) を参照。

---

## スケジュール

### Week 1（Day 1-5）

```mermaid
gantt
    title イテレーション 4 - Week 1
    dateFormat  YYYY-MM-DD
    section US08 候補算出
    OptimalRouteService TDD       :d1, 2026-07-02, 2d
    候補算出 API・候補なし通知     :d2, after d1, 1d
    ワークベンチ画面・ナビ導線     :d3, after d2, 2d
    section 品質
    Try T1/T3/T6 規約・QG 整備    :d4, 2026-07-02, 1d
```

| 日 | タスク |
|----|--------|
| Day 1 | OptimalRouteService 設計・TDD（接続グラフ探索）、Try 規約整備 |
| Day 2 | OptimalRouteService 完成、候補算出 API |
| Day 3 | 候補なし通知、ワークベンチ画面（待ちリスト + 候補算出） |
| Day 4 | ワークベンチ候補一覧表示、経路設計者ナビ導線（H3） |
| Day 5 | US08 統合テスト、QG 条件追加（T6） |

### Week 2（Day 6-10）

```mermaid
gantt
    title イテレーション 4 - Week 2
    dateFormat  YYYY-MM-DD
    section US09/US11
    経路選択確定                  :a1, 2026-07-09, 1d
    RouteConfirmedEvent cross-service :a2, after a1, 2d
    section US12
    荷主通知                       :u1, after a2, 1d
    section 仕上げ
    H4 見積→予約化・統合・MVP 確認 :u2, after u1, 1d
```

| 日 | タスク |
|----|--------|
| Day 6 | US09 経路選択確定（状態遷移 + UI） |
| Day 7 | US11 `RouteConfirmedEvent` 発行（routingms）+ shared 定義 |
| Day 8 | US11 bookingms 購読 + Saga 連携 + cross-service E2E（T2） |
| Day 9 | US12 荷主通知（通知内容・記録・UI） |
| Day 10 | H4 見積→予約化プリセット、統合テスト、Release 1.0 MVP リリース条件確認、デモ準備（US10 ストレッチ着手） |

---

## 設計

> **注**: domain-model.md・data-model.md・ui_design.md の定義に準拠する。`<<新規>>` 印は設計ドキュメント未定義のため IT4 で導入し、設計ドキュメント整合タスクで各設計書に反映する変更点。

### 主要設計方針

- **経路候補算出（routingms ドメインサービス）**: Routing コンテキストの集約は `Voyage` のみ（domain-model.md）。経路候補算出は `Voyage` / `CarrierMovement`（寄港地接続）と `route_design_request`（出発地・目的地・期限・貨物種別）を入力に候補を生成するドメインサービス `OptimalRouteService` として実装。直行便を最優先、所要日数・費用で推奨順ソート、期限内到達不可なら候補なしを返す。候補（`RouteCandidate`）は永続集約を持たず算出結果として返す。
- **逆方向 cross-service（US11、ADR-0009 準拠）**: routingms が経路確定時に shared の `RouteConfirmedEvent` を Kafka 発行 → bookingms が tracking 購読する専用ハンドラ（`RouteConfirmedEventHandler`、route-confirmed プロセッシンググループ）が `AssignRouteToCargoCommand` を発行 → Cargo が `CargoRoutedEvent` を適用して状態を「経路提案中（`ROUTE_PROPOSED`）」に更新、`cargo_leg` を確定。IT3 の bookingms → routingms と対になる routingms → bookingms 方向。
  - **実装注（レビュー H1）**: 当初は「`BookingSagaManager` 経由でコマンド発行」と記述したが、Saga のイベントソース（bookingms 自身の event store）と cross-service イベントの source（Kafka）が競合するため、cross-service イベントは専用 tracking ハンドラで受信して `AssignRouteToCargoCommand` を発行する実装とした。`BookingSagaManager` は割当の結果である `CargoRoutedEvent`（bookingms ローカル）を受けて経路提案中フェーズへ継続する（追跡番号発行は IT5）。
- **経路設計ワークベンチ（S14）**: 経路設計者（ROLE_ROUTING）の複合ビュー `/routing/design/:bookingId`（ui_design.md）。`GET /api/v1/routes/design-requests`（IT3 追加済み）の待ちリスト（arrivalDeadline 昇順、レビュー L4）→ 候補算出 → 選択 → 紐付けを実行（レビュー H3）。

### ドメインモデル

routingms に `OptimalRouteService`（ドメインサービス）と `RouteSearchSpecification`（探索制約）を追加し、bookingms の `Cargo` 集約に経路割当（`AssignRouteToCargoCommand` → `CargoRoutedEvent`）を実装する。`BookingSagaManager` が routingms の `RouteConfirmedEvent` を受けて割当コマンドを発行する。

```plantuml
@startuml
title IT4 ドメインモデル（経路設計 / Saga / cross-service）

package "routingms (Routing Context)" {

  class Voyage <<Aggregate Root>> {
    - voyageNumber: VoyageNumber
    - schedule: Schedule
    - acceptedCargoTypes: Set<CargoType>
    + accepts(cargoType: CargoType): boolean
  }

  class Schedule <<Value Object>> {
    - movements: List<CarrierMovement>
  }

  class CarrierMovement <<Entity>> {
    - departure: Location
    - arrival: Location
    - departureTime: LocalDateTime
    - arrivalTime: LocalDateTime
  }

  class OptimalRouteService <<Domain Service>> {
    + calculate(spec: RouteSearchSpecification, voyages: List<Voyage>): List<RouteCandidate>
    - evaluateConnectivity(legs: List<Leg>): boolean
    - sortByRecommendation(candidates): List<RouteCandidate>
  }

  class RouteSearchSpecification <<Value Object>> <<新規>> {
    - origin: Location
    - destination: Location
    - arrivalDeadline: LocalDate
    - cargoType: CargoType
    + isSatisfiedBy(candidate: RouteCandidate): boolean
  }

  Voyage *-- Schedule
  Schedule "1" *-- "1..*" CarrierMovement
  Voyage *-- "*" CargoType
  OptimalRouteService ..> Voyage : 寄港地接続を評価
  OptimalRouteService ..> RouteSearchSpecification
}

package "Booking Context (bookingms)" {

  class Cargo <<Aggregate Root>> {
    - bookingId: BookingId
    - routeSpecification: RouteSpecification
    - itinerary: CargoItinerary
    - bookingStatus: BookingStatus
    - routingStatus: RoutingStatus
    + handle(AssignRouteToCargoCommand)
    + apply(CargoRoutedEvent)
    + handle(NotifyRouteToShipperCommand) <<新規>>
  }

  class CargoItinerary <<Value Object>> {
    - legs: List<Leg>
    + finalArrivalDate(): LocalDate
    + finalDestination(): Location
  }

  class Leg <<Value Object>> {
    - voyageNumber: VoyageNumber
    - loadLocation: Location
    - unloadLocation: Location
    - loadDate: LocalDateTime
    - unloadDate: LocalDateTime
  }

  enum BookingStatus {
    PRELIMINARY
    ROUTING
    ROUTE_PROPOSED
    CONFIRMED
    CANCELLED
  }

  enum RoutingStatus {
    NOT_ROUTED
    ROUTED
    MISROUTED
  }

  Cargo *-- CargoItinerary
  Cargo *-- BookingStatus
  Cargo *-- RoutingStatus
  CargoItinerary "1" *-- "1..*" Leg
}

class RouteCandidate <<Value Object>> {
  - itinerary: CargoItinerary
  - estimatedDays: int
  - estimatedCost: Money
}

class "BookingSagaManager <<Saga>>" as Saga {
  - bookingId: BookingId
  + on(RouteDesignRequestedEvent)  ' IT3 実装済み
  + on(RouteConfirmedEvent) <<新規>>
  + on(CargoRoutedEvent)
  + on(BookingConfirmedEvent)
}

OptimalRouteService ..> RouteCandidate : 算出（推奨順）
Saga ..> Cargo : RouteConfirmedEvent 受信 →\nAssignRouteToCargoCommand 発行
RouteCandidate *-- CargoItinerary

note bottom of Cargo
  <<新規>> = domain-model.md 未定義。
  US11（ROUTING → ROUTE_PROPOSED 紐付け）で
  AssignRouteToCargoCommand → CargoRoutedEvent、
  US12（荷主通知）で NotifyRouteToShipperCommand を導入し、
  設計ドキュメント整合タスクで domain-model.md へ反映する
end note

note bottom of OptimalRouteService
  Routing の集約は Voyage のみ（domain-model.md）。
  経路候補算出は集約を新設せずドメインサービスで実装。
  routingms → bookingms は RouteConfirmedEvent（shared）の
  Kafka tracking で連携（ADR-0009、IT3 の逆方向）
end note
@enduml
```

#### Cargo 集約の不変条件（IT4 関連）

- `AssignRouteToCargoCommand` は `bookingStatus = ROUTING`（IT3 の US06 で引き渡し済み）のときのみ受理し、`CargoItinerary`（`Leg` 列）を確定して `ROUTE_PROPOSED` に遷移、`routingStatus` を `ROUTED` にする（不正状態時は `IllegalStateException`）
- 紐付ける `CargoItinerary` は出発地が予約の出発地、最終到着地が予約の目的地に一致し、最終到着日が `arrivalDeadline` 以内であること（不変条件違反時は拒否）
- `NotifyRouteToShipperCommand`（US12）は `bookingStatus = ROUTE_PROPOSED` のときのみ受理する
- `bookingStatus = CANCELLED` の Cargo はそれ以降の経路割当・通知コマンドを受け付けない（domain-model.md 準拠）

### 状態遷移（経路設計範囲）

```plantuml
@startuml
title 予約状態遷移（IT4 実装範囲：ROUTING → ROUTE_PROPOSED → CONFIRMED）

[*] --> ROUTING : RequestRouteDesignCommand（US06、IT3 実装済み）
ROUTING --> ROUTE_PROPOSED : AssignRouteToCargoCommand（US11、CargoRoutedEvent）
ROUTE_PROPOSED --> ROUTE_PROPOSED : NotifyRouteToShipperCommand（US12 荷主通知）
ROUTE_PROPOSED --> CONFIRMED : ConfirmBookingCommand（US13、IT3 実装済み）
ROUTE_PROPOSED --> ROUTING : 荷主ルート変更希望（US13 差し戻し、レビュー M6）
ROUTE_PROPOSED --> CANCELLED : CancelBookingCommand（US13、IT3 実装済み）
CONFIRMED --> [*] : 追跡番号発行へ（IT5: US14）

note right of ROUTE_PROPOSED
  本イテレーションの中核遷移。
  routingms の経路確定（US09）→
  RouteConfirmedEvent → Saga →
  AssignRouteToCargoCommand で到達
end note
@enduml
```

### データモデル

経路候補（`RouteCandidate`）は `OptimalRouteService` の算出結果として返し、routingms に永続テーブルは新設しない。US09 経路選択確定・US11 経路紐付け・US12 確定経路通知はいずれも bookingms の既存 `cargo_summary`・`cargo_leg` を更新する。

> **注**: `cargo_summary` / `cargo_leg` / `route_design_request` はいずれも data-model.md で定義済み（同 927-930 行で UC07/09/10 のデータ対応を定義）。本イテレーションで新規テーブルは追加しない。`route_design_request.status` の状態遷移（PENDING → ASSIGNED 等）を扱うかはレビュー M4 として本イテレーションで判断する。

```plantuml
@startuml
hide circle
skinparam linetype ortho

entity "route_design_request\n(routing_read_db, IT3 追加済)" as rdr {
  * **booking_id**: VARCHAR(36) <<PK>>
  --
  origin_unlocode: VARCHAR(5) NOT NULL
  destination_unlocode: VARCHAR(5) NOT NULL
  arrival_deadline: DATE NOT NULL
  cargo_type: VARCHAR(16) NOT NULL
  status: VARCHAR(16) NOT NULL
  ' PENDING（IT3）。状態遷移は本 IT で判断（M4）
  requested_at: TIMESTAMPTZ
}

entity "cargo_summary\n(booking_read_db, US11 で更新)" as cargo {
  * **booking_id**: VARCHAR(36) <<PK>>
  --
  booking_status: VARCHAR(20) NOT NULL
  ' ROUTING → ROUTE_PROPOSED（US11）
  routing_status: VARCHAR(16) NOT NULL
  ' NOT_ROUTED → ROUTED（US11）
  created_at: TIMESTAMPTZ
}

entity "cargo_leg\n(booking_read_db, V002 既存・US11 で確定)" as leg {
  * **booking_id**: VARCHAR(36) <<PK>> <<FK>>
  * **leg_seq**: INTEGER <<PK>>
  --
  voyage_number: VARCHAR(20) NOT NULL
  load_unlocode: VARCHAR(5) NOT NULL
  unload_unlocode: VARCHAR(5) NOT NULL
  load_date: TIMESTAMPTZ NOT NULL
  unload_date: TIMESTAMPTZ NOT NULL
}

entity "voyage\n(routing_read_db, 既存・候補算出の入力)" as voyage {
  * **voyage_number**: VARCHAR(20) <<PK>>
  --
  origin_unlocode / destination_unlocode
  departure_date / arrival_date
}

cargo ||--|{ leg : "1..*（確定旅程）"
rdr ..> cargo : cross-service（US11 経路確定→紐付け）
voyage ..> leg : Leg の voyage_number で参照
@enduml
```

> **検索インデックス（既存）**: 候補算出は `voyage` の `INDEX(origin_unlocode, destination_unlocode, departure_date)` と `voyage_accepted_cargo_type(cargo_type)` を利用（US07 と共通）。`cargo_leg` は `INDEX(voyage_number)`（航海変更時の影響範囲特定）。

### ユーザーインターフェース

> **注**: ui_design.md の画面 ID・パス・ビュー定義に準拠する。S10=予約詳細（営業）、S14=経路設計ワークベンチ（経路設計）。フロントエンドは React + Vite + React Router。フォームは送信成功で詳細へ遷移（PRG 相当）+ バリデーションエラーの自己ループで構成し、htmx は使用しない。フィードバックは IT1-IT3 と同じ alert 表示パターン。

#### ビュー

```plantuml
@startsalt
{+
  S14: 経路設計ワークベンチ（/routing/design/:bookingId）
  {+
    { CargoTracker | 経路設計 | [ログアウト] }
    ----
    {
      {
        予約情報 |
        {
          予約番号 | B-2026-0512-001
          出発地 | JPTYO 東京
          目的地 | DEHAM ハンブルク
          期限 | 2026-08-01
          貨物種別 | 一般 / 8,500 kg
          状態 | 経路設計中（ROUTING）
        }
      } |
      {
        条件調整（US10） |
        到着期限 | "2026-08-01"
        経由地制限 | "なし"
        [ 経路候補を算出 ] | [ 条件を調整して再算出 ]
      }
    }
    ----
    経路候補（US08：推奨順）
    {#
      . | **経由港** | **所要日数** | **概算費用** | **航海番号** | **推奨** | **選択**
      候補1 | JPTYO → DEHAM（直行） | 25 日 | ¥1,650,000 | V-MAERSK-220 | ★ | ( )
      候補2 | JPTYO → SGSIN → DEHAM | 28 日 | ¥1,200,000 | V-MOL-001 | ★ | ( )
    }
    ----
    [ 選択した経路を確定 ]（US09） | [ 経路を予約に紐付け ]（US11）
    ' 期限内候補なし → 「条件を緩和してください」警告（US08）
  }
-----------
  S10: 予約詳細（/bookings/:id）── 経路提案中
  {+
    { CargoTracker | 予約管理 | [ログアウト] }
    ----
    {
      予約 B-2026-0512-001   状態: [経路提案中]
      ----
      {
        {
          確定経路 |
          {
            経由港 | JPTYO → DEHAM（直行）
            所要日数 | 25 日
            到着予定日 | 2026-07-28
            概算料金 | ¥1,650,000
          }
        } |
        {
          予約状態 |
          "● 仮受付"
          "● 経路設計中"
          "● 経路提案中（現在）"
          "○ 予約確定"
        }
      }
      ----
      [荷主に経路を通知]（US12） | [確定]（US13） | [ルート変更] | [キャンセル]
      ' 「荷主に経路を通知」: ROUTE_PROPOSED 時のみ活性（US12）
    }
  }
}
@endsalt
```

#### モデル

```plantuml
@startuml
class 経路設計ワークベンチ {
  bookingId: String
  request: RouteDesignRequestView
  criteria: RouteSearchCriteria
  candidates: List<RouteCandidateView>
  selectedSeq: number
  経路候補を算出()        ' US08: POST /routes/:bookingId/calculate
  条件を調整して再算出()  ' US10
  候補を選択()           ' US09: POST /routes/:bookingId/select
  経路を予約に紐付け()    ' US11: POST /routes/:bookingId/confirm
}

class 経路設計待ちリスト {
  requests: List<RouteDesignRequestView>
  ワークベンチを開く(bookingId)  ' GET /routes/design-requests（arrivalDeadline 昇順）
}

class 予約詳細 {
  booking: BookingDetail
  itinerary: CargoItineraryView
  statusTimeline: List<BookingStatus>
  荷主に経路を通知()  ' US12: POST /bookings/:id/notify-route
  確定()             ' US13（IT3 実装済み）
  アクション活性制御(bookingStatus)
}

class ナビゲーション {
  経路設計()  ' ROLE_ROUTING：経路設計待ちリストへ
  ログアウト()
}

ナビゲーション -* 経路設計待ちリスト
経路設計待ちリスト --> 経路設計ワークベンチ : 行クリック
経路設計ワークベンチ --> 予約詳細 : 紐付け成功（ROUTE_PROPOSED）
@enduml
```

#### インタラクション

```plantuml
@startuml
title 画面遷移図（IT4 経路設計）

[*] --> S01 : ログイン済み（経路設計者）

state "S01 ダッシュボード\n/dashboard" as S01
state "経路設計待ちリスト\n/routing/design" as LIST : route_design_request（arrivalDeadline 昇順、H3）
state "S14 経路設計WB\n/routing/design/:bookingId" as S14 : 候補算出・選択・紐付け
state "S10 予約詳細\n/bookings/:id" as S10 : 確定経路・荷主通知（営業）

S01 --> LIST : サイドナビ「経路設計」（ROLE_ROUTING、H3）
LIST --> S14 : 待ち予約をクリック
S14 --> S14 : 「経路候補を算出」POST /routes/:id/calculate（US08）\n候補なし → 「条件を緩和してください」警告 → 条件調整（US10）
S14 --> S14 : 候補を選択「確定」POST /routes/:id/select（US09）
S14 --> S10 : 「経路を予約に紐付け」POST /routes/:id/confirm（US11）\n→ RouteConfirmedEvent → Saga → ROUTE_PROPOSED（PRG）
S10 --> S10 : 「荷主に経路を通知」POST /bookings/:id/notify-route（US12）\n「確定」POST /confirm（US13、IT3）
@enduml
```

#### フィードバックメッセージ

| 種別 | 契機 | メッセージ例 | スタイル |
|------|------|-------------|---------|
| 成功 | 経路候補算出・選択確定・紐付け・荷主通知 | 「経路候補を 2 件算出しました」「経路を予約に紐付けました（経路提案中）」「荷主に経路を通知しました」 | `alert-success` |
| 警告 | 期限内到達可能な経路候補なし | 「期限内に到達可能な経路がありません。条件を緩和して再算出してください」 | `alert-warning` |
| エラー | バリデーション・不正状態遷移 | 「この予約は経路設計中ではないため経路を紐付けできません」 | `alert-error` |

### API 設計

| メソッド | エンドポイント | 説明 | ストーリー | サービス |
|---------|---------------|------|-----------|---------|
| GET | /api/v1/routes/design-requests | 経路設計待ちリスト（arrivalDeadline 昇順、IT3 追加済み） | US06/US08 | routingms |
| POST | /api/v1/routes/{bookingId}/calculate | 経路候補算出（推奨順・候補なし通知） | US08 | routingms |
| GET | /api/v1/routes/{bookingId}/candidates | 経路候補一覧 | US08/US09 | routingms |
| POST | /api/v1/routes/{bookingId}/select | 経路候補の選択・確定 | US09 | routingms |
| POST | /api/v1/routes/{bookingId}/confirm | 確定経路を予約に紐付け（`RouteConfirmedEvent` 発行） | US11 | routingms |
| POST | /api/v1/bookings/{bookingId}/notify-route | 確定経路を荷主に通知 | US12 | bookingms |

> エンドポイントは実装時に確定し、`docs/design/architecture_backend.md` の API カタログへ随時追記する（レビュー H2 / DoD）。

### ディレクトリ構成

```text
apps/backend/routingms/src/main/java/com/example/routingms/
├─ domain/services/OptimalRouteService.java        # US08 経路候補算出（ドメインサービス）
├─ domain/model/RouteSearchSpecification.java       # 探索制約（出発地・目的地・期限・貨物種別）
├─ domain/events/RouteConfirmedEvent.java（shared 参照） # US11 cross-service
├─ interfaces/rest/RouteController.java              # /api/v1/routes/{bookingId}/*（US08/09/11）
apps/backend/bookingms/src/main/java/com/example/bookingms/
├─ saga/BookingSagaManager.java                     # RouteConfirmedEvent → AssignRouteToCargoCommand
├─ domain/model/Cargo.java                           # handle(AssignRouteToCargoCommand) → CargoRoutedEvent
├─ domain/commands/NotifyRouteToShipperCommand.java  # US12
├─ interfaces/rest/CargoBookingController.java        # POST /bookings/{id}/notify-route
apps/backend/shared/src/main/java/com/example/shared/events/
├─ RouteConfirmedEvent.java                          # cross-service（routingms → bookingms）
apps/frontend/src/features/routing/pages/
├─ RouteDesignListPage.tsx                           # 経路設計待ちリスト（H3）
├─ RouteDesignWorkbenchPage.tsx                      # S14 /routing/design/:bookingId
```

### ADR

| ADR | タイトル | ステータス |
|-----|---------|-----------|
| [ADR-0009](../adr/0009-cross-service-event-saga.md) | cross-service イベント連携と Axon Saga | 承認済み（US11 の routingms → bookingms 逆方向 cross-service にも適用） |

> US11 で `RouteConfirmedEvent`（routingms → bookingms）を追加するため、トピック分割方針（retro Try / レビュー M5）を本イテレーションで ADR に追記検討。

---

## リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| 経路探索アルゴリズム（US08）の複雑度が見積もりを超過 | 高 | まず直行便 + 1 経由までの単純探索で MVP を満たし、多段経由は IT8 バッファ送り。OptimalRouteService をインタフェース化し段階拡張 |
| US11 の逆方向 cross-service で予約状態の二重更新・順序不整合 | 中 | 冪等な受信ハンドラ（IT3 と同方針）+ Saga の状態ガード。Testcontainers Kafka で順序・冪等を検証 |
| Phase 1 完了に US08-12（13 SP）が velocity（10-11）を超過 | 中 | コミットは US08/09/11/12（11 SP）に絞り、US10 はストレッチ。US10 がスリップしても Release 1.0 MVP のコア導線は成立。スリップ時は IT8 バッファで吸収 |
| 経路設計ワークベンチ（新画面）のロール別アクセス制御漏れ | 中 | 経路設計者ロールでの表示制御をフロント + gateway で確認（レビュー懸念対応） |

---

## 完了条件

### Definition of Done

- [x] コードレビュー完了（developing-review、新規 API は architecture_backend.md に追記＝レビュー H2。結果は [it4_routing_review_20260526.md](../review/it4_routing_review_20260526.md)）
- [x] ユニットテストがパス
- [x] E2E テストがパス（cross-service を伴う US11 はイベント駆動 E2E を含む＝retro Try T2）※ Testcontainers Kafka 統合テストで結合検証。Playwright spec のライブ実行は残課題
- [x] ESLint エラーなし / SonarQube Quality Gate PASS（新規コードカバレッジ 80% 以上）※ SonarQube は JaCoCo を代理指標（ライブスキャンは残課題）
- [x] 機能がローカル環境で動作確認済み（local-h2 の route-confirmed プロセッサ起動不具合を修正済み）
- [x] ドキュメント更新完了（iteration_report-4 / API カタログ / data-model）

### デモ項目

1. 経路設計ワークベンチで待ちリストから経路候補を算出・推奨順表示（US08）
2. 候補を選択して確定し、予約に紐付けて予約状態が「経路提案中」に変わる（US09/US11）
3. 営業担当者が確定経路を荷主に通知する（US12）

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-26 | 初版作成（IT3 実績ベロシティ 10 SP・レビュー H3/H4・retro Try を反映） | k2works |
| 2026-05-26 | 進捗同期（tracking-progress --update）：タスク状態・成功基準・DoD を完了状態に更新。ライブ cross-service E2E で検出した `AggregateNotFoundException` 冪等スキップの堅牢化を反映 | k2works |
| 2026-05-26 | Section 5 負債返済・品質を完了：H4 見積→予約化プリセット（TDD）、Try T1（認証ヘッダ規約）・T3（JaCoCo 再生成運用）・T5（フロント E2E チェックリスト）・T6（QG 条件明文化） | k2works |

---

## 関連ドキュメント

- [イテレーション 4 ふりかえり](./retrospective-4.md)（イテレーション終了時に作成）
- [IT3 ふりかえり](./retrospective-3.md)（Try T1-T6 の引き継ぎ元）
- [IT3 レビュー結果](../review/it3_session_review_20260526.md)（H3/H4 の引き継ぎ元）
- [ADR-0009 cross-service イベント連携と Axon Saga](../adr/0009-cross-service-event-saga.md)
