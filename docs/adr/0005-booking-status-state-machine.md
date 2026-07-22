# ADR 0005: 予約状態機械の遷移ルールと不正遷移の拒否（IT4）

## ステータス

承認（IT4 時点）

## コンテキスト

IT4 で US06/US11/US12/US13 を実装するにあたり、予約（`Cargo`）のライフサイクルを状態機械として構築する必要が生じた。着手時点で以下の課題があった。

- `BookingStatus` enum に「経路設計中」に相当する状態が欠落していた（`Preliminary → RouteProposed → Confirmed …`）。しかし [ユーザーストーリー](../requirements/user_story.md) US06 受入 2・US13 受入 4 と [UI 設計](../design/ui_design.md) の予約詳細ボタン設計は「経路設計中」状態を前提としていた
- `Cargo` 集約に状態遷移メソッドが一切なく（`book`/`reconstitute`＋getter のみ）、状態遷移ロジックの置き場所が未定義だった
- 不正な遷移（例: 仮受付から直接確定、確定済みの再確定）をどう拒否するかが未定義だった

## 決定

**予約状態遷移を `Cargo` 集約の `&mut self` メソッドに閉じ込め、不正遷移を `Result::Err(BookingError::InvalidStatusTransition)` で拒否する**。

- `BookingStatus` に `RouteDesigning`（経路設計中）を `Preliminary` と `RouteProposed` の間に追加する
- 遷移メソッド: `request_route_design`（Preliminary→RouteDesigning・US06）/ `propose_route`（RouteDesigning→RouteProposed・US11）/ `confirm`（RouteProposed→Confirmed・US13）/ `revert_to_route_designing`（RouteProposed→RouteDesigning・US13 差し戻し）/ `cancel`（確定前の各状態→Cancelled・US13）
- 各メソッドは現在状態を `match` し、許可された遷移元以外は `BookingError::InvalidStatusTransition { from, action }` を返す。遷移元・操作名をエラーに含め、失敗理由を追跡可能にする
- 通知・確定経路参照といった副作用はアプリケーション層（`BookingLifecycleService`）が担い、ドメインの遷移メソッドは状態遷移のみに専念する（凝集と単一責任）
- `Confirmed` 以降（`TrackingIssued` 等）への遷移は IT5 以降の責務とし、本 IT では `Preliminary`〜`Confirmed`＋`Cancelled` に限定する

## 影響

- 状態遷移ロジックが 1 箇所（`Cargo`）に集約され、不正遷移をコンパイル済みコードの実行時ガードで一貫拒否できる。UI ボタンの出し分けは UX 上の補助であり、正しさはドメインが担保する
- インフラ層は `CargoRepository::save` の upsert で `booking_status` を反映すればよく、遷移の正当性検証を持たない
- `BookingStatus` が 8 値から 9 値になり、永続化文字列（`ROUTE_DESIGNING`）・[data-model](../design/data-model.md) の許容値・[domain-model](../design/domain-model.md) の状態一覧を同期した
- US12 の荷主通知など「状態を変えないが特定状態でのみ許される操作」も、アプリケーション層で現在状態を検証してからドメイン副作用を呼ぶ（[IT4 レビュー](../review/it4_development_review_20260722.md) programmer 中1 対応）
- 通知の実配信（メール/SMS）は本 IT では「送信＝記録」に限定した意図的な負債であり、`NotificationPort` の抽象で後続の実装差し替えに備える
