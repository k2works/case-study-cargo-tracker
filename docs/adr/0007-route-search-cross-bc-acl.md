# ADR 0007: 経路探索の BC 横断を合成ルート注入方式で実現し、探索アルゴリズムを段階実装する

IT4（US08 経路候補算出・US09 経路確定）で、Routing Context の経路探索結果を Booking / Estimation Context が利用する BC 横断の実現方式と、経路探索アルゴリズムの方針を定める。

日付: 2026-07-25

## ステータス

2026-07-25（IT4）承認・実装で完遂。

## コンテキスト

US09 は Routing Context が算出した経路候補を Booking Context の `Cargo` に `CargoItinerary` として割り当てる BC 横断操作である。T3（見積候補の実経路化）でも Estimation Context が同じ経路探索を利用する。BC 独立性（`make arch` / go-arch-lint）を保ちつつ、以下を両立する必要があった。

1. Booking / Estimation が Routing の探索ロジックを再利用する（重複実装の回避）。
2. 消費側 BC（Booking / Estimation）の infrastructure が Routing の application を直接 import しない（越境依存の回避）。
3. 既存 ACL 先例（ADR-0005: 自 BC の sqlcgen で他 BC テーブルを業務識別子で直読・他 BC の application 非依存）と構造的に整合する。

また、US08 の経路探索は「航海スケジュール群を接続グラフとみなした経路列挙」であり、多段乗り継ぎまで一度に実装すると見積を超過するリスクがあった。

## 決定

1. **合成ルート注入方式（`.go-arch-lint.yml` 無改変）を採る**。
   - 消費側 BC の application に **ACL ポート `RouteSearcher`** を定義する（Booking は `internal/booking/application`、Estimation は `internal/estimation/application`）。ポートは自 BC の語彙（Booking は `RouteCandidateDTO`/`RouteLegDTO`、Estimation は `RouteCandidateResult`）で候補を受け取る。
   - Routing は経路探索ユースケース `SearchRoutesService` を公開し、結果を **公開 DTO `RouteCandidateView`**（domain 型を漏らさない）で返す。
   - **合成ルート（`cmd/server`）が変換アダプタを注入**する。アダプタは Routing の `SearchRoutesService` を呼び、公開 DTO を消費側 BC のポート型へ写像する。`cmd` は既に routing-application への依存が許可済みのため、**アーキテクチャルールを一切変更せずに BC 横断を実現**する。
   - **不採用**: 「booking-infrastructure → routing-application を go-arch-lint で許可」する案は、前例のない越境依存をルールに導入するため採らない。

2. **経路探索は Routing domain のドメインサービス `RouteFinder` に隔離する**。航海スケジュール群を接続グラフとみなし、空間連結（前区間の荷降地＝次区間の積込地）・時刻連続（次の積込は前の荷降以降）・貨物種別対応・到着期限内を満たす経路を DFS で列挙し、直行優先・推奨順（区間数少→所要日数→費用）にソートする。境界（直行・経由・接続不能・期限超過・貨物種別非対応）をユニットテストで隔離検証する。

3. **探索深度と費用モデルを段階実装する**。探索深度は `maxRouteLegs`（現状 4）で上限を設け、費用は区間数・所要日数ベースの簡易モデルとする。多段乗り継ぎの深掘り・費用精緻化は後続イテレーションに委ねる。

4. **確定経路の保持先は Delivery 経由とする**。`Cargo → Delivery → RoutingStatus`（ADR-0003 の RoutingStatus 正典）とし、IT4 では `Delivery` を `routingStatus` のみの最小 VO として導入する。`transportStatus`・最終荷役は IT6 で追加する。US09 の経路確定は `Delivery.routingStatus=ROUTED` で、`BookingStatus` は `ROUTE_PROPOSED` のまま（予約確定 CONFIRMED は US13）。

## 影響

- `RouteSearcher` ポートは Booking / Estimation の application に定義し、合成ルートで Routing の `SearchRoutesService` を変換注入する（実装済み）。
- `leg` テーブル（`cargo_id` 経由・航海参照は業務識別子 `voyage_number`）と `cargo.routing_status` 列を追加（migration 000009）。`route_candidate.waypoints`（見積の経由港・migration 000010）。
- `domain-model.md` の RouteCargoCommand を「Delivery.routingStatus=ROUTED・BookingStatus 不変」に是正。`ui_design.md` の `/route` ロールを経路設計者に是正。
- 探索深度・費用モデルの精緻化は後続 IT の技術的負債として明示する。

## 参考

- [ADR-0002](0002-bounded-context-canon.md) BC 正典
- [ADR-0003](0003-transport-status-canon.md) TransportStatus / RoutingStatus 正典
- [ADR-0005](0005-bc-reference-and-shared-sqlcgen.md) BC 間参照・ACL パターン
- [IT4 計画](../development/iteration_plan-4.md)
