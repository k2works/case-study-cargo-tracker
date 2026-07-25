# ADR 0006: CargoType を共有カーネルへ昇格し、航海スケジュールのドメインモデルを拡張する

Phase 2（経路設計）着手にあたり、複数 BC で共有される貨物種別 `CargoType` の配置と、航海スケジュール（Voyage）のドメインモデル拡張方針を定める。

日付: 2026-07-25

## ステータス

2026-07-25（IT3）承認。決定1（CargoType 昇格）は実装で完遂。決定2（Voyage 拡張）は IT3 の Routing Context 実装で反映する。

## コンテキスト

IT3 開始準備の横断整合検証（validating-design 軸 C）で、以下の設計上の問題が検出された。

1. **CargoType の BC 独立性違反リスク**: 貨物種別 `CargoType`（GENERAL / HAZARDOUS / REFRIGERATED）は Booking Context の `internal/booking/domain` に定義されている。IT3 で新設する Routing Context（`Voyage.supportedCargoTypes`）と Estimation Context（`Estimate.cargoType`）がこれを参照すると、**新 BC が Booking BC の domain に依存する BC 独立性違反**となり、`make arch`（go-arch-lint）で越境検出される。ドメインモデル設計（domain-model.md）では CargoType を「Booking Context と共通」（Estimation Context 節）と位置づけており、意味的には共有カーネルに属する。

2. **航海スケジュール（Voyage）の列不足**: US24（航海スケジュール新規登録）の受入基準は「航海番号・船名・運送会社・出発/到着港・出発/到着日・対応貨物種別・寄港地」の入力を要求するが、現行の `voyage` テーブル・`Voyage` 集約は `voyage_number` と `carrier_movement`（区間）のみで、船名・運送会社・対応貨物種別の情報を保持できない。

## 決定

1. **CargoType を共有カーネル（`internal/shared/domain`）へ昇格する**。IT2 の `ShipperCode` 昇格（ADR-0005 決定2）と同型のパターンを踏襲し、`CargoType`・定数・`ParseCargoType`・`ErrUnknownCargoType` を `shared/domain` に定義する。Booking Context は利便のため型エイリアス（`type CargoType = shared.CargoType`）と定数・関数の再エクスポートで互換を保つ。Routing / Estimation は `shared.CargoType` を直接参照する。これにより新 BC は Booking に依存せず、`make arch` の BC 独立性検証を通過する。

2. **Voyage 集約・voyage テーブルを拡張する**。`Voyage` 集約に `vesselName`（船名）・`carrier`（運送会社）・`supportedCargoTypes`（対応貨物種別の集合）を追加し、`voyage` テーブルに `vessel_name`・`carrier`・`supported_cargo_types` 列を追加する。対応貨物種別は US07（航海検索）で危険物・冷凍の絞り込みに用いる。

## 影響

- `domain-model.md` の共有カーネル節に `CargoType` を追記し、Booking Context の CargoType 記述を「共有カーネル参照」に改める（IT3 で反映）。
- `data-model.md`・`domain-model.md` の Voyage に vessel_name / carrier / supported_cargo_types を追記する（IT3 で反映）。
- Booking の型エイリアスは恒久的な互換シムとして残す（Booking も本質的には共有カーネルの CargoType を用いるため問題ない）。将来 Booking 側参照を `shared.CargoType` に統一するかは任意。

## 参考

- [ADR-0002](0002-bounded-context-canon.md) BC 正典
- [ADR-0005](0005-bc-reference-and-shared-sqlcgen.md) BC 間参照・共有 sqlcgen（ShipperCode 昇格パターン）
- [IT3 計画](../development/iteration_plan-3.md)
