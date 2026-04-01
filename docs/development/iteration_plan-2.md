---
title: イテレーション 2 計画
description: 輸送見積作成と最適ルート検索の実装計画。外部ルート照会の stub 化と見積・検索 UI の整備を中心に進める。
published: true
date: 2026-04-01T00:00:00.000Z
tags: iteration-plan, it2
---

# イテレーション 2 計画

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 2 |
| **期間** | Week 3-4（2026-04-14〜2026-04-27） |
| **ゴール** | 輸送見積を作成し、予約または見積条件をもとに最適ルート候補を検索できるようにする |
| **目標 SP** | 10 |

---

## ゴール

### イテレーション終了時の達成状態

1. **輸送見積**: 営業担当者が輸送条件と貨物条件から見積を作成し、見積番号付きで保存できる
2. **ルート検索**: 経路設計者が予約情報または見積条件をもとに外部ルート照会を実行し、候補一覧を確認できる
3. **品質維持**: backend テスト、E2E、SonarQube Quality Gate の基準を維持したまま IT2 機能を追加できる

### 成功基準

- [ ] 見積作成画面で出発地・目的地・希望期限・貨物種別・重量を入力し、見積番号を発行できる
- [ ] 外部ルート照会の結果として、経由港・所要日数・概算料金・航海番号を含む複数候補を表示できる
- [ ] 希望期限に間に合うルートがない場合のメッセージ表示と代替候補提示ができる
- [x] ルート照会失敗時に stub / 代替候補で動作確認できる（StubQuoteRouteProviderAdapter 実装済み）
- [ ] backend テスト、E2E、SonarQube Quality Gate が Green を維持する
- [ ] テストカバレッジ 80% 以上

---

## ユーザーストーリー

### 対象ストーリー

| ID | ユーザーストーリー | SP | 優先度 |
|----|-------------------|----|--------|
| US01 | 輸送見積を作成する | 5 | 必須 |
| US06 | 最適ルートを検索する | 5 | 必須 |
| **合計** | | **10** | |

### ストーリー詳細

#### US01: 輸送見積を作成する

**ストーリー**:
> 営業担当者として、荷主の輸送要件（出発地・目的地・希望期限・貨物種別・重量）を入力し、輸送料金と所要日数の見積を作成したい。なぜなら、荷主が予算と納期を事前に把握でき、予約決定を迅速に行えるからだ。

**受入条件**:

1. 出発地・目的地・希望期限・貨物種別・重量を入力できる
2. 外部経路システムへのルート照会が行われ、複数のルート候補が表示される
3. ルート候補ごとに「経由港・所要日数・概算料金・航海番号」が表示される
4. 見積情報が保存され、見積番号が発行される
5. 希望期限に間に合うルートが存在しない場合、その旨が通知される
6. 外部経路システムへの接続失敗時は過去の実績データから代替候補が表示される

#### US06: 最適ルートを検索する

**ストーリー**:
> 経路設計者として、予約番号を指定して出発地・目的地・希望着日から最適ルート候補を検索したい。なぜなら、ルート候補を迅速に把握し、荷主に最適な輸送プランを提案できるからだ。

**受入条件**:

1. 予約番号を指定して予約情報（出発地・目的地・希望着日・貨物種別）を確認できる
2. 外部経路システムへのルート照会が行われる
3. ルート候補が「経由港・所要日数・費用・航海番号」とともに一覧表示される
4. 希望着日に間に合うルートがない場合、その旨が表示され代替条件での再検索ができる
5. 危険物または冷凍貨物の場合、取扱い可能なルートのみが表示される
6. 出発地・目的地は UN/LOCODE 形式で指定できる

### タスク

#### 1. US01: 輸送見積を作成する（5 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 1.1 | Quote 集約・値オブジェクト（QuoteId、QuoteCondition、QuoteOption）を設計する | 4h | Copilot | [x] |
| 1.2 | 見積ドメインモデルと料金算出ルールのユニットテストを追加する | 3h | Copilot | [x] |
| 1.3 | 外部ルート照会ポートと WireMock / stub アダプターを実装する | 4h | Copilot | [x] |
| 1.4 | QuoteRepository・MyBatis mapper・Flyway migration を追加する | 3h | Copilot | [x] |
| 1.5 | 見積作成ユースケース、Web / REST エンドポイント、保存後の見積番号表示を実装する | 4h | Copilot | [x] |
| 1.6 | 見積作成画面と候補一覧 UI、統合テスト、E2E を追加する | 2h | Copilot | [ ] |

**小計**: 20h（理想時間）

#### 2. US06: 最適ルートを検索する（5 SP）

| # | タスク | 見積もり | 担当 | 状態 |
|---|--------|---------|------|------|
| 2.1 | 予約番号起点のルート検索クエリと Read Model を設計する | 3h | Copilot | [x] |
| 2.2 | Booking 情報と route provider を接続する検索サービスを実装する | 4h | Copilot | [x] |
| 2.3 | 希望着日不一致・危険物 / 冷凍貨物の絞り込みルールを実装しテストする | 4h | Copilot | [ ] |
| 2.4 | ルート候補一覧 UI、代替条件での再検索導線、404 / 業務エラー表示を実装する | 4h | Copilot | [ ] |
| 2.5 | REST API / MVC テスト、E2E、Swagger 表示確認を追加する | 3h | Copilot | [ ] |
| 2.6 | SonarQube と docs 更新を含めた品質ゲート確認を行う | 2h | Copilot | [ ] |

**小計**: 20h（理想時間）

#### タスク合計

| カテゴリ | SP | 理想時間 | 状態 |
|---------|----|---------|------|
| US01 輸送見積 | 5 | 20h | 進行中（5/6 タスク完了） |
| US06 最適ルート検索 | 5 | 20h | 進行中（2/6 タスク完了） |
| **合計** | **10** | **40h** | |

**1 SP あたり**: 4h  
**進捗率**: 58%（7/12 タスク完了）

---

## スケジュール

### Week 1（Day 1-5: 2026-04-14〜2026-04-18）

```mermaid
gantt
    title IT2 - Week 1
    dateFormat  YYYY-MM-DD
    section US01 見積
    Quote モデル設計・テスト        :it2w1a, 2026-04-14, 1d
    Route port / stub 実装         :it2w1b, after it2w1a, 1d
    Repository / migration         :it2w1c, after it2w1b, 1d
    section UI / API
    見積ユースケース・Web / REST     :it2w1d, after it2w1c, 1d
    見積画面・候補一覧 UI           :it2w1e, after it2w1d, 1d
```

| 日 | タスク |
|----|--------|
| Day 1 | Quote モデル設計、値オブジェクト、ユニットテスト |
| Day 2 | route provider ポート、WireMock / stub、接続失敗時代替候補設計 |
| Day 3 | QuoteRepository、MyBatis mapper、Flyway migration |
| Day 4 | 見積作成ユースケース、Web / REST エンドポイント |
| Day 5 | 見積 UI、候補一覧表示、Week 1 動作確認 |

### Week 2（Day 6-10: 2026-04-21〜2026-04-25）

```mermaid
gantt
    title IT2 - Week 2
    dateFormat  YYYY-MM-DD
    section US06 ルート検索
    予約起点検索サービス設計        :it2w2a, 2026-04-21, 1d
    条件絞り込み・代替候補          :it2w2b, after it2w2a, 1d
    検索 UI / REST                 :it2w2c, after it2w2b, 1d
    section 品質
    MVC / REST / E2E テスト         :it2w2d, after it2w2c, 1d
    Quality Gate・docs・デモ準備    :it2w2e, after it2w2d, 1d
```

| 日 | タスク |
|----|--------|
| Day 6 | 予約番号起点の検索サービス、Read Model、Booking 連携 |
| Day 7 | 希望着日不一致・危険物 / 冷凍貨物ルール、代替条件検索 |
| Day 8 | 検索 UI、REST 表示、例外時メッセージ |
| Day 9 | MVC / REST / E2E テスト、Swagger 掲載確認 |
| Day 10 | SonarQube、docs 更新、バグ修正、デモ準備 |

---

## 設計

### ドメインモデル

```plantuml
@startuml IT2_domain_model
skinparam classBackgroundColor #FAFAFA
skinparam classBorderColor #999

class Quote <<Aggregate Root>> {
  -QuoteId id
  -QuoteCondition condition
  -List<RouteOption> options
  +issue()
}

class QuoteId <<ValueObject>>
class QuoteCondition <<ValueObject>>
class RouteOption <<ValueObject>>
class RouteProvider <<Port>>
class Booking

Quote *-- QuoteId
Quote *-- QuoteCondition
Quote o-- RouteOption
Quote ..> RouteProvider
Booking ..> RouteProvider
@enduml
```

### データモデル

```plantuml
@startuml IT2_data_model
hide circle
skinparam linetype ortho

entity "quotes" as quotes {
  *id : uuid
  --
  quote_number : varchar
  origin_locode : varchar
  destination_locode : varchar
  cargo_type : varchar
  weight_kg : decimal
  requested_arrival_date : date
  created_at : timestamp
}

entity "quote_options" as quote_options {
  *id : uuid
  --
  quote_id : uuid
  voyage_no : varchar
  route_path : varchar
  lead_time_days : int
  estimated_fee : decimal
}

quotes ||--o{ quote_options
@enduml
```

### ユーザーインターフェース

#### ビュー

```plantuml
@startsalt
{+
  見積作成画面
  {+
    出発地(UN/LOCODE) | "JPTYO"
    目的地(UN/LOCODE) | "USNYC"
    希望期限 | "2026-04-25"
    貨物種別 | [GENERAL_CARGO]
    重量(kg) | "1200"
    [ 見積作成 ]
  }
  ==
  ルート候補一覧
  {+
    航海番号 | 経由港 | 所要日数 | 概算料金
  }
}
@endsalt
```

#### モデル

```plantuml
@startuml IT2_ui_model
class QuoteForm {
  originLocode
  destinationLocode
  requestedArrivalDate
  cargoType
  weightKg
}

class QuoteResultView {
  quoteNumber
  routeOptions
}

QuoteForm --> QuoteResultView
@enduml
```

#### インタラクション

```plantuml
@startuml IT2_flow
[*] --> 見積作成
見積作成 --> 候補一覧 : 見積成功
見積作成 --> 見積作成 : 入力エラー
候補一覧 --> 再検索 : 条件変更
再検索 --> 候補一覧 : 再検索成功
@enduml
```

---

## 計画調整メモ

- IT1 実績ベロシティは 10 SP のため、IT2 も 10 SP を維持します。
- IT1 で基盤整備が完了しているため、IT2 は環境構築タスクを含めず機能開発へ集中します。
- 外部経路システムはまず stub / WireMock を前提にし、実 API 連携は IT3 以降で契約テストへ拡張します。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-04-01 | IT2 計画を作成 | Copilot |
| 2026-04-01 | タスク 1.5, 2.2 完了を反映（進捗 58%） | Copilot |
