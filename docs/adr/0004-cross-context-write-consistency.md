# ADR 0004: BC 跨ぎ書き込みの一貫性方針（IT4）

## ステータス

承認（IT4 時点）

## コンテキスト

IT4 で経路確定・予約紐付け（US09/US11）を実装した際、経路設計画面の「この経路で確定し予約に紐付ける」操作（`interface-web::route_confirm`）が **2 つの境界づけられたコンテキスト（BC）にまたがる書き込み**を行う構造になった。

1. **Routing Context**: `RoutePlanningService::confirm_route` が確定経路を `selected_route` / `selected_route_leg` に永続化する（1 トランザクション）
2. **Booking Context**: `BookingLifecycleService::propose_route` が `Cargo` を `RouteDesigning → RouteProposed` に遷移させ `cargo.booking_status` を更新する（別トランザクション）

両者は BC 独立性（[ADR-0003](0003-dependency-injection-composition-root.md) の依存方針・[開発戦略](../development/development_strategy.md) の「コンテキスト間を直接参照しない」）を保つため、それぞれ独立した出力ポート（`SelectedRouteRepository` / `CargoRepository`）を持ち、単一の DB トランザクションで束ねていない。このため、**手順 1 が成功し手順 2 が失敗すると「確定経路は保存済みだが予約状態は経路設計中のまま」という中間状態**が残りうる（[IT4 レビュー](../review/it4_development_review_20260722.md) architect 指摘）。

分散トランザクション（2PC）や単一トランザクションで 2 BC のテーブルを束ねる選択肢もあるが、前者は複雑度が高く、後者は BC 独立性を壊す（Booking のユースケースが Routing のテーブルを知ることになる）。

## 決定

**BC 跨ぎの書き込みは単一トランザクションで束ねず、各 BC 内のトランザクション整合＋冪等リトライで結果整合に収束させる**方針を採る。

- `route_confirm` は「確定経路保存（Routing）→ 予約状態遷移（Booking）」の順に逐次実行する。順序は「下流の事実（確定経路）を先に確定し、それを指す状態遷移を後に行う」に固定する
- `CargoRepository::save` は `ON CONFLICT (booking_id) DO UPDATE`（upsert）で冪等であり、`SelectedRouteRepository::save` も `booking_id` 業務キーの upsert ＋子区間の洗い替えで冪等である。したがって**同一操作の再実行は安全**で、中間状態が残った場合はユーザーの再操作（同じ確定操作）で収束する
- 状態遷移メソッド（`propose_route`）は `RouteDesigning` からのみ成功し、既に `RouteProposed` なら不正遷移として弾かれる。二重実行で状態が進みすぎることはない
- 中間状態を検知・自動修復する補償処理（サガ）は本 IT では導入しない。件数規模・業務影響が小さく、YAGNI とする

## 影響

- BC 独立性を維持したまま経路確定→予約紐付けを実装できる（`domain-booking → domain-routing` の依存を張らない）
- 部分失敗時に「確定経路あり・予約は経路設計中」の中間状態が一時的に発生しうる。UI 上は経路設計画面から再度確定でき、冪等性により収束する
- 将来、通知・イベント発行を含む多段の BC 跨ぎ処理が増える場合は、ドメインイベント＋アウトボックスパターン（`infra-eventbus` 骨格）による結果整合の明示化を再検討する（IT4-5 の持ち越し）
- 監視観点では「確定経路はあるが `booking_status != ROUTE_PROPOSED/CONFIRMED` の予約」を将来の健全性チェック対象として認識しておく
