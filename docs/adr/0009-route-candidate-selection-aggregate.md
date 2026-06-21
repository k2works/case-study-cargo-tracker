# 0009 経路選択を独立集約 `RouteCandidateSelection` として永続化する

US09（経路選択・確定）で営業担当者が選んだ経路を「予約 → 選択された航海列 + 確定状態」として永続化する集約を Routing Context に新設する。

日付: 2026-06-21

## ステータス

2026-06-21 提案されました（IT4 タスク 1.1）

## コンテキスト

IT3 で導入した `RouteCandidate`（`cargotracker.routing.domain.model.valueobjects.RouteCandidate`）は経路探索の結果（`List[RoutingLeg]`）を表す値オブジェクトであり、永続化を想定していない。

IT4 US09 では:

1. 経路候補画面に複数候補を一覧表示し、営業担当者が 1 つを選択する
2. 選択操作で「この予約はこの経路で行く」という意思決定を永続化する
3. 後続の US11（予約への紐付け）/ US12（荷主通知）/ US13（予約確定）が選択結果を参照する

選択結果の永続化対象は以下を満たす必要がある：

- 業務キー: 予約番号（1 予約 1 選択）
- 構成情報: 選択された経路を構成する `Voyage` の順序リスト
- 状態: `Pending`（選択操作前）/ `Confirmed`（確定済）
- 楽観ロック: `version`

## 決定

### (a) 新集約 `RouteCandidateSelection` を Routing Context に追加

```scala
final case class RouteCandidateSelection private (
    bookingId: String,
    voyages: List[VoyageNumber],
    status: RouteSelectionStatus,
    version: Int
):
  def confirm: Either[Error, RouteCandidateSelection]
```

- 業務キー: `bookingId`（Booking Context への ID 参照のみ、ACL は applicationservice 側で吸収）
- 不変条件: `voyages.nonEmpty`
- 状態遷移: `Pending → Confirmed`（再確定は `AlreadyConfirmed` で拒否）

### (b) `RouteSelectionStatus` enum を Routing Context valueobjects に追加

```scala
enum RouteSelectionStatus:
  case Pending, Confirmed
```

`BookingStatus`（Booking Context）の `RouteProposed` / `RouteAssigned` とは別軸の状態。Routing Context 内部での経路選択の確定有無を表す。

### (c) `RouteCandidate`（値オブジェクト）と `RouteCandidateSelection`（集約）を分離

- `RouteCandidate`（VO）: 探索アルゴリズムの結果。複数候補をリストで保持し画面表示に使う。永続化しない
- `RouteCandidateSelection`（集約）: 営業担当者の意思決定の記録。永続化する

命名の類似による混同を避けるため、本 ADR で「`Selection` サフィックスは確定意思の保持」と定義する。

### (d) 永続化スキーマ

Flyway V9 で `route_candidate_selection` テーブルを新設（IT4 タスク 1.2、`data-model.md` 既追記済）。

- `booking_id` を UNIQUE 制約（1 予約 1 選択）
- `voyage_numbers` はカンマ区切り VARCHAR(200)（将来 N:N が必要になれば別テーブルへ正規化、ADR 追補）
- `status` CHECK 制約で `Pending` / `Confirmed` を許容

## 結果

### 利点

- 値オブジェクトの探索結果と集約の意思決定が分離され、責務が明確化
- 楽観ロック + 状態遷移ルールを集約内に閉じ込められる
- US11/US12/US13 が `RouteCandidateSelection` を起点に組み立てやすい

### 欠点・トレードオフ

- `RouteCandidate` と `RouteCandidateSelection` の命名類似による初学者の混乱（本 ADR で命名規則を明示）
- 1 予約に対する経路選択履歴（やり直しの記録）は本集約では持たない（次回 IT で `notification_log` 同様、別ログテーブルを検討）

### 既存設計への影響

- Booking Context 側は `Cargo.assignItinerary(Itinerary)` で経路を受け取る（IT4 タスク 2.1）。`Itinerary` 値オブジェクトは Booking Context 内部に新設し、ACL は applicationservice で `RouteCandidateSelection.voyages` → `Itinerary` に変換する

## 代替案

### 代替案 1: Booking Context に `Itinerary` 集約を持たせ Routing 側は永続化しない

US09 の選択結果を Booking Context 側に直接保存し、Routing 側は値オブジェクトのみ。Booking Context の責務が肥大化し、確定前の `Pending` 状態の表現が Booking 側のステートマシンに混ざるため不採用。

### 代替案 2: `Cargo` 集約に `selectedVoyages` を生やす

`Cargo` に直接フィールドを追加する案。シンプルだが「未確定の経路選択」も `Cargo` に乗ることになり、`BookingStatus` の状態遷移と Routing の選択状態が二重管理になる。

### 代替案 3: `RouteCandidate` 値オブジェクトに状態を生やす

VO に状態を持たせると不変性が崩れ、永続化対象と非永続対象の区別が曖昧になる。DDD の集約と値オブジェクトの区別を保つため不採用。

## 関連

- ADR 0005 経路探索アルゴリズム（DFS + 深さ制限）
- ADR 0006 航海データモデル追補
- ADR 0008 queryservices 命名規約拡張
- IT4 タスク 1.1（本 ADR の対象）/ 1.2（V9 マイグレーション）/ 1.3（SelectRouteCommand）
- `domain-model.md` BookingStatus（`RouteAssigned` 追加）
- `data-model.md` `route_candidate_selection` テーブル
