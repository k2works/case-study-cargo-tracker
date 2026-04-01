---
name: acl_adapter_pattern
description: booking → routing ACL アダプターパターン（CargoType 変換含む）
type: project
---

`booking.infrastructure.adapters.BookingQueryPortAdapter` は routing コンテキストの `BookingQueryPort` を実装する ACL アダプター。

## CargoType 変換（booking → routing）

| booking.CargoType | routing.CargoType |
|---|---|
| GENERAL_CARGO | GENERAL |
| DANGEROUS_GOODS | HAZARDOUS |
| REFRIGERATED | REFRIGERATED |

## フィールドマッピング

| TransportCondition | BookingSnapshot |
|---|---|
| originLocation() | originLocode |
| destinationLocation() | destinationLocode |
| requestedDeliveryDate() | requestedArrivalDate |
| CargoSpecification.weightKg() | weightKg |

**Why:** booking と routing は別コンテキストで同名の enum（CargoType）を持つが異なる。アダプター側で switch 式で変換することでコンテキスト間の汚染を防ぐ。

**How to apply:** routing から booking の情報を取得する際は必ずこのアダプター経由にする。直接 `booking` パッケージを `routing` から import しない。
