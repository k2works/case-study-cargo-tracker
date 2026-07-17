# ADR-0013: 料金算出と Billing↔Booking 連携は合成層と状態射影で行う

精算（US21-US23）における料金算出の基礎データ解決と、精算完了時の予約 Settled 同期を、合成層の ACL と Booking 状態の射影更新で実装する決定。

日付: 2026-07-17

## ステータス

- 2026-07-17 提案（IT7・Billing コンテキスト立ち上げ・Release 1.1）
- 2026-07-17 改訂（IT8 task4.1）: **精算完了の Settled 同期を状態射影更新（案）から `BookingSettled` イベント駆動の集約更新（案 C）へ移行**。

> **改訂の要点（IT8）**: 当初の「決定 3. Settled 同期は状態射影の更新（`CargoQueries.syncBookingStatus` によるガードなし `UPDATE`）」を廃止し、`Billing.confirmPayment` 成功後に合成層が `Booking.Application.RouteAssignment.settle`（`CargoRepository` 経由で Cargo 集約を再構成し `Settle` コマンドを適用）を呼ぶ方式に変更した。これにより **Delivered→Settled の遷移ガードを集約で通す**（Delivered でない予約は Settled にならない）。`syncBookingStatus` は削除。E2E/受け入れテストで `cargo.booking_status` の実値が `SETTLED` になることを検証する（IT7 レビュー高#1・retro-7 Try#3）。案 C は当初「保留」だったが、Delivered/Settled が旅程（leg）を持つ集約として実装済みのため再構成コストは許容範囲と判断した。

## コンテキスト

IT7 で Billing コンテキスト（精算書発行・法人割引・入金確認）を実装する。料金算出（US21）は配送完了した予約の輸送実績（重量・貨物種別・距離）から基本料金を算出し、法人割引（US22）を適用して精算書を発行、入金確認（US23）で精算を完了する。

ここで 2 つの横断連携が必要になる。

1. **料金算出の基礎データ解決**: Billing の料金算出は Booking の貨物（重量・貨物種別）と Shipper の法人判定を要する。Billing ドメインはこれらを直接参照できない（BC 分離・ADR-0001）。
2. **精算完了の予約同期**: 開発戦略は「料金算出（Delivered 制限）」、domain-model ビジネスルール 1 は「Invoice は Delivered 後に発行」を定める。精算完了で予約状態を `Settled` に同期する必要がある。現状 `BookingState` は Confirmed まで（IT4）で `Delivered`・`Settled` は未実装。

## 決定

**料金算出の基礎データ解決は合成層（`CargoTracker.Web`）の ACL クエリで行い、精算完了の予約 Settled 同期は Booking 状態の射影更新で行う。`BookingState` に `Delivered`・`Settled` を段階追加する。**

1. **合成層 ACL クエリ**: 料金算出時、合成層が `CargoQueries.findChargeBasis`（重量・貨物種別・荷主 ID・予約状態）と `ShipperQueries.isCorporateByUuid`（法人判定）を呼び、Billing の `Charge.calculateBase` と `DiscountPolicy` へ渡す。Billing ドメインは Booking/Shipper の型を参照しない（IT4 の RouteAcl・IT5 の HandlingAcl と同方針）。
2. **BookingState の段階追加**: `BookingState` に `Delivered of CargoItinerary`・`Settled of CargoItinerary` を追加し、`MarkDelivered`（Confirmed→Delivered）・`Settle`（Delivered→Settled）コマンドと `CargoDelivered`・`BookingSettled` イベントを追加する（iteration_plan-4 で「TrackingIssued→…→Settled は IT5+」と予告済みの段階拡張・[[adr-migration-via-maybe]] 方式）。
3. **Settled 同期は状態射影の更新**: 入金確認成功後、合成層が `CargoQueries.syncBookingStatus` で `cargo.booking_status` を `SETTLED` に射影更新する。旅程（leg テーブル）を伴う集約再構成を要さず、BC 連携の状態射影のみを更新する（Tracking の `transport_status` 射影と同方針・ADR-0002 の post-commit ベストエフォート）。

### 代替案

- **案 B: Billing ドメインが Booking/Shipper を直接参照**（却下）: BC 分離（ADR-0001・ArchUnit）に抵触する。
- **案 C: 精算完了同期を Cargo 集約の再構成＋ execute で行う**（保留）: ドメイン的に最も正確だが、Confirmed/Delivered/Settled は旅程（leg）を要し集約再構成が重い。将来、精算完了をイベント（`BookingSettled`）駆動で正式に集約更新する余地を残し、本 IT は射影更新に留める。
- **案 D: 料金算出を Delivered 状態でのみ許可する厳格ゲート**（部分採用）: 戦略の Delivered 制限に忠実だが、Booking の Delivered 実体化（Tracking 引取済→Booking Delivered 同期）を完全結線する必要がある。本 IT は Tracking の「引取済（Claimed）」を配送完了の実質的契機とし、Booking Delivered/Settled は状態射影で表現する割り切りとする。

## 影響

### ポジティブ

- Billing ドメインが Booking/Shipper を参照せず、横断解決が合成層に閉じる。ArchUnit の BC 分離を維持できる（IT4/IT5 の ACL と一貫）。
- `BookingState` に Delivered/Settled を段階追加したことで、予約ライフサイクル（Confirmed→Delivered→Settled）が型で表現され、将来の集約駆動同期への移行余地を残す。
- 射影更新により、leg 未整備の予約でも精算完了同期が機能する。

### ネガティブ

- 精算完了の Settled 同期が集約の execute を経ず射影更新のため、Booking 集約の不変条件（状態遷移ガード）をバイパスする。ドメイン的な厳密性は将来のイベント駆動同期に委ねる。
- 料金算出の Delivered 制限が「引取済（Tracking Claimed）」の割り切りで、Booking Delivered の完全結線は未達。

## コンプライアンス

- Billing ドメインが Booking/Shipper を直接参照しないことを ArchUnitNET で確認する。
- 料金算出→法人割引→精算書発行→入金確認→Settled 同期が受け入れテストで一気通貫することを確認する。
- `BookingState` の Delivered/Settled 遷移（`MarkDelivered`/`Settle`）と不正遷移拒否をドメインユニットで確認する。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（垂直スライス・BC 分離）、ADR-0002（post-commit イベント）、ADR-0007（BookingState DU 拡張）、ADR-0010（合成層 ACL 変換）、`docs/design/domain-model.md`（Billing Context §6・Cargo BookingState）、`docs/development/development_strategy.md`（終盤・Delivered 制限）、`docs/development/iteration_plan-7.md`（タスク 3.4）。
