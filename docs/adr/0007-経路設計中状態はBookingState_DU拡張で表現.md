# ADR-0007: 「経路設計中」状態は BookingState DU の拡張で表現する

貨物予約の「経路設計依頼済み（提案前）」状態を、補助カラムではなく `BookingState` 判別共用体のケース追加で表現する決定。

日付: 2026-07-15

## ステータス

2026-07-15 提案

## コンテキスト

IT2 の US06「予約情報を経路設計者に引き渡す」では、受入基準として「経路設計依頼を実行すると、予約状態が『経路設計中』に更新される」ことが求められます。しかし現行のドメインモデル（`docs/design/domain-model.md`）の `BookingState` 判別共用体は以下の遷移で定義されており、「経路設計依頼済みだが経路提案前」を表す状態ケースを持ちません。

```fsharp
type BookingState =
    | Preliminary
    | RouteProposed of CargoItinerary      // 経路提案済み（US08-10 の算出後）
    | Confirmed of CargoItinerary
    | TrackingIssued of CargoItinerary * TrackingNumber
    | InTransit of ...
    | Delivered of ...
    | Settled of ...
    | Cancelled of reason: string
```

一方、データモデル（`docs/design/data-model.md`）には将来追加予定カラムとして `routing_status`（値: `ROUTED` / `MISROUTED` / `NOT_ROUTED`、追加時期: Routing Context 実装＝IT4 以降）が定義されています。これは**経路決定の結果**を表す概念であり、「経路設計依頼済み（提案前）」という予約ライフサイクル上の状態とは別物です。

設計ドキュメントレビュー（2026-07-06）の中優先度指摘 #262 で「`routing_status` と `BookingState` の二重管理にならないか確認」が挙げられており、状態管理の一元化が求められています。

「経路設計中」状態の表現方法を決定しないと、US06 の状態遷移（`Preliminary → 経路設計中 → RouteProposed`）が実装できず、営業担当者から経路設計者への業務引き渡しの導線が成立しません。

## 決定

**「経路設計依頼済み（提案前）」状態を `BookingState` 判別共用体に `RoutingRequested` ケースとして追加します。** `routing_status` 補助カラムでの表現は採用しません。

### 変更箇所

1. **BookingState DU の拡張**（`docs/design/domain-model.md`）:

   ```fsharp
   type BookingState =
       | Preliminary
       | RoutingRequested                     // ← 追加：経路設計依頼済み（提案前）
       | RouteProposed of CargoItinerary
       | ...
   ```

2. **コマンドと状態遷移**（`Cargo.execute`）に経路設計依頼の遷移を追加:

   ```fsharp
   type BookingCommand =
       | SubmitForRouting                     // ← 追加：US06 経路設計依頼
       | ProposeRoute of CargoItinerary
       | ...

   // 遷移規則（execute のパターンマッチ）
   | Preliminary, SubmitForRouting ->
       Ok ({ cargo with State = RoutingRequested }, [ RoutingRequested cargo.BookingId ])
   | RoutingRequested, ProposeRoute itinerary -> ...   // 既存の Preliminary→RouteProposed を置換
   ```

3. **永続化マッピング**（`data-model.md` の `cargo.booking_status`）: `booking_status` の文字列表現・CHECK 制約に `ROUTING_REQUESTED` を追加。`BookingState.toString` / `ofString` に対応ケースを追加する。

4. **ドメインイベント**: 経路設計依頼時に post-commit で経路設計者向け通知イベント（`RoutingRequested of BookingId` 等）を発行する（ADR-0002 準拠）。

### 代替案

- **案 B: `routing_status` 補助カラムで表現**（却下）: `booking_status` は `Preliminary` のまま補助カラムで「経路設計中」を表す。DU 変更は不要だが、(1) `routing_status` は経路決定結果（ROUTED/MISROUTED/NOT_ROUTED・IT4+）を表す別概念であり意味論的に流用は不適切、(2) 状態と付随データの整合を型で保証する F# 版の設計原則（domain-model「不正状態を表現不能にする」）に反し、`booking_status` と補助フラグの二重管理（レビュー #262 の懸念）を招く。
- **案 C: `RouteProposed` を再利用し空旅程で表現**（却下）: `RouteProposed of CargoItinerary` に空の旅程を入れて代用する案。`CargoItinerary` は非空リスト制約（`CargoItinerary.create` スマートコンストラクタ）を持つため型として成立せず、不正状態を招く。

## 影響

### ポジティブ

- 予約ライフサイクルの状態が `BookingState` 一箇所で一元管理され、`routing_status`（経路決定結果）との責務分離が明確になる（レビュー #262 解消）。
- 「経路設計依頼済みなのに旅程を持つ」等の不正状態が型レベルで排除される（F# 版設計原則の徹底）。
- `Cargo.execute` の網羅的パターンマッチにより、経路設計依頼を経ない不正遷移（`Preliminary` から直接 `ProposeRoute`）がコンパイル時／実行時に検出可能になる。

### ネガティブ

- `BookingState` に依存する既存の全パターンマッチ（`Cargo.execute`・`stateName`・`toString`／`ofString`・永続化マッピング）の再検証が必要。DU ケース追加は網羅性検査に影響するため、追加後に必ずフルテスト（ユニット + 統合 + Arch）を実行する。
- 既存の遷移 `Preliminary → RouteProposed`（IT3-IT4 で実装予定）を `RoutingRequested → RouteProposed` に置き換えるため、Routing Context 実装時（IT3+）に整合を取る必要がある。
- data-model の `booking_status` CHECK 制約・初期スキーマに `ROUTING_REQUESTED` を追加する（DbUp forward-only マイグレーション。ADR-0003 準拠）。

## コンプライアンス

- `Cargo.execute` のパターンマッチが `RoutingRequested` を含む全状態 × コマンドを網羅し、許可されない遷移が `InvalidStateTransition` を返すことをユニットテストで確認する。
- `booking_status` カラムの値集合と `BookingState` DU ケースが `toString`／`ofString` で往復一致することをラウンドトリップテストで確認する。
- DU ケース追加後に `dotnet test`（フル）を実行し、網羅性警告ゼロ・全テスト緑・ArchUnit ルール緑を確認する（DU 拡張時のフルテスト実行を規律とする）。
- `routing_status` カラムを予約ライフサイクル状態の判定に使用しないこと（経路決定結果の記録に限定）をコードレビューで確認する。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（垂直スライス）、ADR-0002（post-commit イベント）、ADR-0003（DbUp forward-only）、`docs/design/domain-model.md`（BookingState DU・Cargo.execute）、`docs/design/data-model.md`（cargo.booking_status・routing_status）、`docs/review/設計ドキュメント_review_20260706.md`（中指摘 #262）、`docs/development/iteration_plan-2.md`（US06・タスク 3.1）。
