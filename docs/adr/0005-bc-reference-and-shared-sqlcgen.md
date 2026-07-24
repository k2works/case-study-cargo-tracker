# ADR 0005: BC 間参照は業務識別子（shipper_code）で行い、共有 sqlcgen の扱いを定める

境界付けられたコンテキスト間の参照キーと、sqlc 生成コードの配置方針を定める。

日付: 2026-07-24

## ステータス

2026-07-24 承認されました（暫定。IT2 以降で ShipperCode 型への改称と sqlc パッケージ分割を再評価する）

## コンテキスト

IT1 のマルチパースペクティブレビュー（2026-07-24）で、アーキテクト・プログラマー両視点から以下の構造的指摘（重要度「高」）が挙がった。

1. **共有カーネル `ShipperId` の意味二重化**: ドメインモデルでは `ShipperId <<shared kernel>>` を UUID と定義しているが、実装では Shipper 側で生成した UUID は永続化されず（shipper テーブルに UUID 列がない）、Booking 側は `RegisterCargoService` で `shipper_code`（業務コード）を `shared.NewShipperId()` に包んで参照している。同一の `ShipperId` 型が Shipper では UUID、Booking では shipper_code という異なる識別子を運んでおり、共有カーネルの前提（全 BC で同一の意味）が崩れている。
2. **共有 sqlcgen による隠れた BC 結合**: sqlc 生成コードを全 BC 共通の `internal/shared/infrastructure/sqlcgen/` に集約している。`shared` は go-arch-lint の `commonComponent` のため構造検証を無検出で通過するが、Booking の Repository と Shipper の Repository が同一の `sqlcgen.Queries`（全テーブル型・全クエリを含む単一 struct）を共有し、BC 横断で他 BC のテーブルへ型レベルにアクセスできる状態になっている。

いずれも go-arch-lint が緑でも構造的に BC 独立性を侵食しうる箇所であり、方針の明文化が必要である。

## 決定

1. **BC 間参照は業務識別子で行う**。Booking Context から Shipper Context への参照は、内部 ID（UUID）ではなく業務識別子 **`shipper_code`（`SHP-` プレフィックス、UNIQUE 制約付き）** をキーとする。ACL ポート `ShipperExistenceChecker` は `shipper_code` で存在確認する。cargo テーブルの荷主参照列も `shipper_code`（VARCHAR）であり、DB レベルの数値 FK 結合は用いない（BC 独立性の維持）。
2. **共有カーネルの識別子型を意味に合わせて整理する（IT2 で実施）**。BC 間参照キーは `ShipperCode`（業務識別子）として型名・意味を正し、UUID の内部 ID は Shipper BC 内部に閉じる（永続化するか廃止するかは IT2 で決定）。IT1 時点では既存実装（`shared.ShipperId` に code を格納）を許容し、本 ADR で意味を「BC 間参照キー＝業務コード」と固定する。
3. **共有 sqlcgen は暫定的に許容し、BC 越境アクセスを規律で禁止する**。単一 DB・単一マイグレーション運用のため sqlc 生成コードは当面 `internal/shared/infrastructure/sqlcgen/` に集約する。ただし各 BC の Repository は **自 BC のテーブルに対応するクエリのみを使用する**ことを規律とし、他 BC のテーブル型・クエリを参照しない。IT2 で sqlc の `output` を BC 別パッケージ（例: `booking/infrastructure/sqlcgen`）へ分割し、`.go-arch-lint.yml` の当初想定（BC 別 infrastructure）に実装を寄せることを再評価する。

## 影響

- BC 独立性は「業務識別子による参照 + ACL」で維持される。DB スキーマ変更が BC をまたいで波及しない。
- 共有カーネルの型の意味が IT1 時点では厳密でない（`ShipperId` に code を格納）。IT2 の `ShipperCode` 改称までの技術的負債として本 ADR に記録する。
- 共有 sqlcgen は BC 越境アクセスを構造的には防げないため、コードレビューと本規律で担保する。IT2 の分割で構造的強制へ移行する。
- 関連して、domain-model.md・data-model.md の `ShipperId`（UUID）定義および cargo.shipper_id（BIGINT FK）記述を、本方針（shipper_code 参照）に合わせて是正する必要がある（IT1 の iteration_plan-1 注記済み。IT2 で反映）。

## コンプライアンス

- `ShipperExistenceChecker` の統合テストで `shipper_code` による存在確認（存在・不在）を検証する（実施済み）。
- `shipper_code` の UNIQUE 制約をマイグレーションで担保する（実施済み: 000001）。
- IT2 で sqlc パッケージ分割時は、go-arch-lint に BC 別 sqlcgen コンポーネントを定義し、BC 越境参照が検出されることを確認する。

## 備考

著者: 開発チーム（Claude Code 支援）。背景は docs/review の IT1 マルチパースペクティブレビュー（2026-07-24）のアーキテクト・プログラマー指摘を参照。IT2 で「ShipperCode 型への改称」「sqlc パッケージ分割」「domain-model/data-model の是正」を Try として実施する。
