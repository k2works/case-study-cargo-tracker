# 0004 Cross-BC 参照に `ShipperRef` VO を導入する

Booking BC が Shipper BC の `ShipperId` を直接 import している既知違反 (7 件) を、Shared に置く Cross-BC 参照値オブジェクトに置換することで、arch-check Phase 2 (Rule 6) の gate を実現可能にする

日付: 2026-06-29

## ステータス

2026-06-29 提案 (IT3 で実装着手、Phase 1 として Booking のみ移行)

## コンテキスト

IT1 で Booking BC を実装した際、Shipper BC の `Cargotracker.Shipper.Domain.Model.Value.ShipperId` を Booking 側から直接 import していた。これは arch-check の Rule 4 (Bounded Context 間で他 BC の Domain を直接参照しない) に違反する。

IT1 リトロでは「IT2 / IT3 で ACL 経由に解消」と決め、`scripts/arch-check.sh` に `ALLOWLIST_RULE4` として 7 ファイルを許容例外として登録していた:

```
Cargotracker/Booking/Application/Ports.hs
Cargotracker/Booking/Application/RegisterBookingCommand.hs
Cargotracker/Booking/Domain/Model/Cargo.hs
Cargotracker/Booking/Infrastructure/PostgresBookingRepository.hs
Cargotracker/Booking/Infrastructure/PostgresShipperExistenceChecker.hs
Cargotracker/Booking/Views/BookingShowView.hs
Cargotracker/Booking/Views/BookingListView.hs
```

IT2 マルチパースペクティブレビュー M-04 で「ACL パターン統一方針 ADR 起票」が指摘され、IT3 では U-05 で「ShipperRef VO 導入 + ALLOWLIST 解消」を計画した。本 ADR でその規約を確定する。

## 検討した選択肢

| 案 | 内容 | 評価 |
| :--- | :--- | :--- |
| A. ALLOWLIST 維持 | 既知違反として明示し続ける | Rule 6 gate を恒久的に無効化することになる。新規 BC 追加時に判断が割れる |
| B. Booking 側で `Text` を直接保持 | 業務識別子文字列のみ Booking が保持 | 型レベルの保証が消える。新 BC ごとに同じパターンが再発生する |
| C. Shared に Cross-BC 参照 VO を導入 (本 ADR の採用案) | `Cargotracker.Shared.Domain.Reference.ShipperRef` を新設し、Booking 側はこれを保持 | 型安全性を維持しつつ Booking が Shipper BC から完全に独立する |
| D. ACL ポートに新インターフェース定義 | `BookingShipperRepository` のような Booking 専用 Read Model 取得 ACL | 既存 `ShipperExistenceChecker` 以上の要件が見えていないため過剰設計 |

案 C を採用する。

## 決定

### 規約 BCE-04: Cross-BC 参照には `Shared.Domain.Reference.<TargetBC>Ref` を導入する

Cross-BC 参照を必要とする識別子は、Shared カーネル内の `Reference` モジュール配下に置く参照型 (`<TargetBC>Ref`) を経由する。

```haskell
-- src/Cargotracker/Shared/Domain/Reference/ShipperRef.hs
module Cargotracker.Shared.Domain.Reference.ShipperRef
  ( ShipperRef (..)
  , mkShipperRef
  ) where

newtype ShipperRef = ShipperRef { unShipperRef :: Text }
```

### 利用ルール

| ルール | 内容 |
| :--- | :--- |
| BCE-04.1 | Cross-BC 参照を保持する BC は `Shared.Domain.Reference.<TargetBC>Ref` のみ import する。`<TargetBC>.Domain.Model.Value.*Id` を直接 import してはならない |
| BCE-04.2 | 参照型は `newtype` で識別子文字列を包む。スマートコンストラクタで検証規則を持つ |
| BCE-04.3 | 参照型のスマートコンストラクタは識別子の **構造** (例: `SHP-XXXXXX`) を検証する。**実在性** (実際に Shipper が存在するか) は ACL Port (`ShipperExistenceChecker` 等) が SQL 等で別途解決する |
| BCE-04.4 | ACL Port 実装 (Infrastructure 層) では `Shared.Domain.Reference.<TargetBC>Ref` から `<TargetBC>` 側のサロゲートキー / 集約 ID への変換 SQL を許容する |

### 段階移行計画

| 段階 | 内容 | タイミング |
| :--- | :--- | :--- |
| **Phase 1** (本 IT3) | `ShipperRef` を新設し、Booking BC の 7 ファイルで `ShipperId` → `ShipperRef` に置換。`ALLOWLIST_RULE4` の Booking 系 7 エントリを削除 | IT3 U-05 |
| **Phase 2** (IT4) | Estimation BC の `inputShipperId :: Text` を `ShipperRef` に格上げ。新規追加 BC (Tracking 等) は最初から `ShipperRef` を使用 | IT4 |
| **Phase 3** (IT5+) | ALLOWLIST 機構を arch-check.sh から削除 (`ShipperRef` 規約と arch-check Phase 2 Rule 6 で完全に gate) | IT5+ |

## 影響

### 影響を受けるモジュール (Phase 1)

| モジュール | 変更 |
| :--- | :--- |
| `Cargotracker.Shared.Domain.Reference.ShipperRef` | 新規作成 |
| `Cargotracker.Booking.Application.Ports` | `BookingRepository` / `ShipperExistenceChecker` のシグネチャを `ShipperRef` に変更 |
| `Cargotracker.Booking.Application.RegisterBookingCommand` | `inputShipperId :: Text` → `mkShipperRef` 経由 |
| `Cargotracker.Booking.Domain.Model.Cargo` | `cargoShipperId :: ShipperId` → `ShipperRef` |
| `Cargotracker.Booking.Infrastructure.PostgresBookingRepository` | Cargo 構築時に `ShipperRef` を経由 |
| `Cargotracker.Booking.Infrastructure.PostgresShipperExistenceChecker` | `exists :: ShipperRef -> IO Bool` で受け、SQL で `shipper.shipper_code` と照合 |
| `Cargotracker.Booking.Views.BookingShowView` / `BookingListView` | `ShipperRef` から文字列を取り出して表示 |
| `apps/cargo-tracker/scripts/arch-check.sh` | `ALLOWLIST_RULE4` から Booking 系 7 エントリを削除 |

### CI / arch-check への影響

- Phase 1 完了後、Booking BC の Shipper BC 直接参照は 0 件。Rule 4 検査は ALLOWLIST に頼らず通る
- arch-check Phase 2 (U-04 で実装予定) の Rule 6 (Interfaces → Domain) は本規約と独立。本 ADR は Rule 4 専用の解決策

### ロールバック

- Phase 1 のロールバックは `ShipperRef` を削除し `ShipperId` import を復元するだけで完了。`ALLOWLIST_RULE4` も Git history から復元可能

## 関連 ADR

- [ADR-0002](0002-arch-check-implementation.md) Rule 4 ALLOWLIST が本 ADR で恒久解消される
- [ADR-0005](0005-bounded-context-error-types.md) BC 固有エラー分離と同じく、BC 境界を型レベルで強制する方針

## 参照

- [IT2 マルチパースペクティブレビュー](../review/it2_code_review_20260627.md) M-04
- [イテレーション 3 計画](../development/iteration_plan-3.md) §1.5 (タスク U-05)
- [ドメインモデル設計](../design/domain-model.md) §1 Booking Context
