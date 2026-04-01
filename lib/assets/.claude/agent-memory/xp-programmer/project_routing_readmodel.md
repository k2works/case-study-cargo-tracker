---
name: routing_readmodel_design
description: routing コンテキストの Read Model 設計パターン（IT2 US06 タスク 2.1 で実装）
type: project
---

IT2 US06 タスク 2.1 で routing コンテキストに以下の Read Model を実装した。

## ファイル構成

**ドメインモデル** (`routing.domain.model`):
- `CargoType.java` — routing 固有の貨物種別 enum（GENERAL / HAZARDOUS / REFRIGERATED）
- `RouteSearchQuery.java` — ルート検索条件 record（compact constructor でバリデーション）
- `RouteCandidate.java` — ルート候補 record（List.copyOf() でイミュータブル保証）

**アウトバウンドポート** (`routing.application.internal.outboundservices`):
- `BookingQueryPort.java` — booking コンテキストへのアクセス（依存性逆転）
- `BookingSnapshot.java` — booking → routing のデータ転送 record
- `RouteProviderPort.java` — 外部ルートプロバイダーへのポート

**アプリケーションサービス** (`routing.application.internal.queryservices`):
- `RouteSearchService.java` — `searchByBookingId(UUID)` と `searchByCondition(RouteSearchQuery)`
- `BookingDataNotFoundException.java` — 予約データ不在時の例外（"BookingNotFoundException" と重複しないよう "Data" サフィックス付き）

## 設計上の決定

- `routing` → `booking` の直接依存を避けるため `BookingQueryPort` + `BookingSnapshot` で ACL 実装
- `booking.BookingNotFoundException` との MyBatis エイリアス衝突を避けるため `BookingDataNotFoundException` に命名

**Why:** booking コンテキストの `BookingNotFoundException` と同名クラスを作ると MyBatis TypeAlias 衝突が発生する。
