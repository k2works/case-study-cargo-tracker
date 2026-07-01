# 0008 Itinerary + Leg を Booking 集約配下に配置

確定経路 (Itinerary) と区間 (Leg) を Booking BC の Cargo 集約配下のエンティティとして配置し、`ItineraryRepository` ポートで永続化する規約

日付: 2026-06-30

## ステータス

採用 (2026-07-01、IT5 Ralph Loop iter 6 で `PostgresItineraryRepository` 実装完了 + iter 10 で ADR 昇格)

採用判断の根拠: IT4 で `ItineraryRepository` ポート + `ConfirmRouteCommand` (Domain + Application) を実装、IT5 で `PostgresItineraryRepository` (BIGSERIAL PK + UUID UK、`executeMany` で leg 一括挿入、T-02 準拠で Tx 境界は Application 層に委譲) を実装し `stack build --fast` 成功。migration 2 本 (`20260831110000_create_itinerary_and_leg.sql` + `20260831110100_extend_cargo_for_confirmation.sql`) も投入済み。1 Tx で itinerary + leg + cargo 更新を括る設計が Postgres 層で成立する見込みが立った。

引き続きの残タスク:
- ConfirmRouteCommand の Tx 境界 (Application 層で `withDbTransaction` を張り、itinerary + leg + cargo 状態更新を 1 Tx 化)
- hspec-wai 統合テストで actual Tx ロールバック検証 (IT5 task 3.1)

## コンテキスト

US09「経路を選択・確定する」で確定経路を表現するモデルが必要になった。設計の選択肢は以下。

* (A) **Routing BC の集約として配置**: Voyage と同じ BC に置く。ただし「確定経路」は予約に紐付く概念であり、Voyage マスタとはライフサイクルが異なる
* (B) **独立 BC として配置 (Routing Confirmed Context 等)**: 純粋すぎる分割で BC 数の肥大化リスク
* (C) **Booking BC の Cargo 集約配下に配置** (採用): Booking が経路を所有する業務概念と一致。BookingId と 1:0..1 の関係で永続化も自然

加えて、永続化方式として以下を検討。

* (X) `BookingRepository` に統合 (`saveBookingWithItinerary`)
* (Y) `ItineraryRepository` を分離ポートとして追加 (採用)

xp-architect レビュー (IT4) で (X) を推奨されたが、Phase C で 1 Tx に束ねる前提なら現状の (Y) でも Tx 境界を Application 層で明示できるため、本 ADR では (Y) を維持しつつ Postgres 層で 1 Tx を保証する責務分担を決める。

## 決定

**Itinerary + Leg は Booking BC の Cargo 集約配下** に配置し、`ItineraryRepository` ポートで永続化する。1 Tx は Application 層 (`ConfirmRouteCommand`) が `withDbTransaction` を張って保証する。

### 規約 IT-01: Itinerary は集約内エンティティ

* `Itinerary` は Cargo の状態 (`RouteAssigned` 以降) に従属する
* Cargo を削除 (将来) すれば Itinerary も削除される (ON DELETE CASCADE 相当)
* 直接 Itinerary だけを取り出す API は禁止 (`findItineraryById` は内部参照専用)

### 規約 IT-02: Leg は Itinerary 内エンティティ

* `Leg` は Itinerary に従属し、独立した識別子 (UUID 等) を持たない
* `seq_number` で順序を保証 (1, 2, 3, ... の連番)
* スマートコンストラクタ `mkLeg` で空文字 / 順序違反 / 時刻違反を拒否

### 規約 IT-03: Itinerary の不変条件

* 1 つ以上の Leg を持つ (空 Itinerary 禁止)
* 隣接 Leg の接続性: `leg[i].unloadLocation == leg[i+1].loadLocation`
* 隣接 Leg の時刻順序: `leg[i].unloadTime <= leg[i+1].loadTime`
* `seq_number` は 1 から始まる連番

検証は `mkItinerary` 純粋関数で完結 (T-03)。

### 規約 IT-04: ItineraryRepository ポート

```haskell
data ItineraryRepository m = ItineraryRepository
  { saveItinerary             :: BookingId -> Itinerary -> m (Either DomainError ())
  , findItineraryByBookingId  :: BookingId -> m (Maybe Itinerary)
  , findItineraryById         :: ItineraryId -> m (Maybe Itinerary)
  }
```

* `saveItinerary` は `Connection` を引数で受け取らず、リポジトリ実装内部で接続管理する想定
* T-02 規約により `withTransaction` は Repository 内では呼ばない → Application 層が `withDbTransaction` で束ねる

### 規約 IT-05: ConfirmRouteCommand の Tx 境界

```haskell
confirmRoute :: ConfirmRouteInput -> AppM (Either DomainError ConfirmRouteResult)
confirmRoute input = withDbTransaction $ \tx -> do
  -- 1. Cargo 取得 + linkRoute (RouteProposed -> RouteAssigned)
  -- 2. Itinerary 構築 (mkItinerary 検証)
  -- 3. saveItinerary (failure 時は Tx ロールバック)
  -- 4. updateBooking (Cargo 更新 + itinerary_id 紐付け)
  -- すべて成功なら commit、いずれか失敗で全体ロールバック
```

IT4 段階では Application 層の execute が 2 つの port を順次呼ぶ実装 (Tx 境界はまだ Repository 側にある暫定)。IT5 で `withDbTransaction` を Application 層に移し、ALLOWLIST_T01_T02 を解消する。

### 規約 IT-06: ItineraryId は UUID v4

* 推測不能性が必要なため UUID 形式 (`Estimate.estimate_id` と同方針)
* スマートコンストラクタ `mkItineraryId` で 36 文字 + 8-4-4-4-12 区切りを検証

### 規約 IT-07: ADR-0004 Cross-BC 規約の適用

* `Leg` の `voyageNumber` / `loadLocation` / `unloadLocation` は **Text として保持** する
* Routing BC の `VoyageNumber` や `Shared.UnLocode` を直接 import しない
* HTTP ハンドラ (Interfaces 層) が Routing BC の型と相互変換する責務を持つ

## 影響

### データモデル (IT5 マイグレーション)

```sql
-- 012_create_itinerary_and_leg.sql
CREATE TABLE itinerary (
    id            BIGSERIAL PRIMARY KEY,
    itinerary_id  UUID NOT NULL UNIQUE,
    booking_id    VARCHAR(20) NOT NULL REFERENCES booking(booking_id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE leg (
    id                          BIGSERIAL PRIMARY KEY,
    itinerary_id                UUID NOT NULL REFERENCES itinerary(itinerary_id) ON DELETE CASCADE,
    seq_number                  INT  NOT NULL,
    load_location_unlocode      VARCHAR(5) NOT NULL,
    unload_location_unlocode    VARCHAR(5) NOT NULL,
    load_time                   TIMESTAMPTZ NOT NULL,
    unload_time                 TIMESTAMPTZ NOT NULL,
    voyage_number               VARCHAR(20) NOT NULL,
    UNIQUE (itinerary_id, seq_number),
    CHECK (load_time < unload_time)
);

-- 013_extend_booking_for_confirmation.sql
ALTER TABLE booking
  ADD COLUMN itinerary_id UUID REFERENCES itinerary(itinerary_id),
  ...;
```

### 影響を受けるモジュール

| 層 | モジュール | 変更 |
| :--- | :--- | :--- |
| Domain | `Booking.Domain.Model.Value.ItineraryId` (新規) | UUID v4 VO |
| Domain | `Booking.Domain.Model.Leg` (新規) | Leg エンティティ + mkLeg |
| Domain | `Booking.Domain.Model.Itinerary` (新規) | Itinerary エンティティ + mkItinerary + itDepartureTime/itArrivalTime/itPorts |
| Application | `Booking.Application.ItineraryPorts` (新規) | ItineraryRepository ポート |
| Application | `Booking.Application.ConfirmRouteCommand` (新規) | Itinerary 構築 + linkRoute + 両 Repo 永続化 |
| Infrastructure | `Booking.Infrastructure.PostgresItineraryRepository` (IT5 新規) | dbmate migration 012/013 + 1 Tx 実装 |
| Views | `Booking.Views.RouteConfirmView` | 経路選択 radio + 確定ボタン |

### テスト戦略

* `ItinerarySpec.hs`: 14 件 (UUID 検証 / Leg 順序 / Itinerary 接続性 + 時刻 + 連番)
* `ConfirmRouteCommandSpec.hs`: 4 件 (正常 / NotFound / Draft 拒否 / 接続性違反)
* IT5 で `PostgresItineraryRepositorySpec` (testcontainers) を追加

## 段階移行計画

| 段階 | タイミング | 内容 |
| :--- | :--- | :--- |
| Phase 0 (IT4) | 2026-06-30 | 本 ADR 提案 + Domain + Application + UI 実装完了 |
| Phase 1 (IT5) | 未着手 | PostgresItineraryRepository 実装 + `ConfirmRouteCommand` に `withDbTransaction` 導入 + ALLOWLIST_T01_T02 から `PostgresItineraryRepository` を除外 + ADR 採用昇格 |
| Phase 2 (IT5+) | 未着手 | 既存 Postgres*Repository 3 件の T-01/T-02 違反解消 (ALLOWLIST_T01_T02 ゼロ化) |

## 関連

* [iteration_plan-4.md](../development/iteration_plan-4.md) US09 / Task 2.1 / Task 2.2
* [it4_code_review_20260630.md](../review/it4_code_review_20260630.md) H-03 / C-01
* [ADR-0002 arch-check 実装](0002-arch-check-implementation.md) T-01 / T-02 規約
* [ADR-0004 Cross-BC ShipperRef](0004-cross-bc-shipper-ref.md) Text 識別子規約
