# ADR 0003: TransportStatus の正典値と MISROUTED の帰属

共有カーネル TransportStatus の列挙値を確定し、MISROUTED を RoutingStatus に帰属させる。

日付: 2026-07-11

## ステータス

2026-07-11 承認されました

## コンテキスト

TransportStatus（輸送状態）は Booking と Tracking が共有する Shared Kernel の列挙型だが、設計ドキュメント間で `IN_TRANSIT` / `CUSTOMS_INSPECTION` / `MISROUTED` を含む体系と、`ONBOARD_CARRIER` / `CLAIMED` / `EXCEPTION` / `UNKNOWN` を含む体系の 2 系統が混在していた（設計レビュー 2026-07-11 の重要度「高」指摘 #2）。共有型の値がずれるとイベント同期時に不正値が発生する。また MISROUTED（誤配送）が輸送状態か経路妥当性かの帰属も曖昧だった。

## 決定

1. TransportStatus の正典値は次の 9 値とする（原典 Cargo Tracker の HandlingEvent 由来の体系を採用）:
   `NOT_RECEIVED` / `RECEIVED` / `LOADED` / `ONBOARD_CARRIER` / `UNLOADED` / `AWAITING_CLAIM` / `CLAIMED` / `EXCEPTION` / `UNKNOWN`
2. **MISROUTED は TransportStatus ではなく RoutingStatus**（`NOT_ROUTED` / `ROUTED` / `MISROUTED`）の値とする。荷役妥当性判定（旅程外の港での LOAD/UNLOAD 等）により RoutingStatus が MISROUTED となり、Cargo 集約の Delivery に反映される。
3. BookingStatus（8 値: `PRELIMINARY` / `ROUTE_PROPOSED` / `CONFIRMED` / `TRACKING_ISSUED` / `IN_TRANSIT` / `DELIVERED` / `SETTLED` / `CANCELLED`）は予約ライフサイクルを表す別の状態機械であり、TransportStatus と混同しない。BookingStatus の `IN_TRANSIT` は予約フェーズの表現として維持する。
4. これら状態列挙の正典は docs/design/domain-model.md とする。

## 影響

- 3 つの状態機械（BookingStatus / TransportStatus / RoutingStatus）の責務が明確になり、テスト仕様（状態遷移マトリクス）の基準が一意に定まる。
- UI のステータスバッジ表示は TransportStatus（追跡系画面）と BookingStatus（予約系画面）を画面種別で使い分ける。使い分けルールの明文化は UI 設計側の課題として残る（レビュー指摘 #16）。
- DB の CHECK 制約・sqlc の型定義はこの正典値に従う。

## コンプライアンス

- 状態機械の単体テストは domain-model.md の遷移定義をテスト仕様の基準とし、遷移マトリクスの網羅テストで検証する。
- 列挙値の追加・変更は domain-model.md の改訂と本 ADR の更新を必須とする。

## 備考

著者: 開発チーム（Claude Code 支援）。背景は docs/review/design_go_review_20260711.md 改善提案 #2・#3 を参照。
