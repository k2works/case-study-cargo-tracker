# ADR-0008: 荷主の横断参照は ShipperId（Guid）の永続化により業務識別子で行う

Booking Context から Shipper Context への参照キーを ShipperId（Guid）に定め、Shipper 側で ShipperId を永続化する決定。

日付: 2026-07-15

## ステータス

2026-07-15 提案

## コンテキスト

IT2 の US04（貨物予約登録）では、`ShipperExistenceChecker` ACL（domain-model: `ShipperId -> Async<bool>`）で荷主の存在を確認したうえで予約を登録する。ドメインの `Cargo` 集約は荷主を共有カーネルの `ShipperId`（Guid ベース）で参照する。

しかし IT1 の Shipper 永続化（`ShipperRepository.save`）は、`ShipperId`（Guid）を保存しておらず、`ShipperCode`（`SHP-` + Guid の先頭 8 文字）のみを `shipper` テーブルに保存している。`ShipperCode` は Guid の先頭 8 桁のみで元の Guid を復元できないため、`Cargo` が保持する `ShipperId`（完全な Guid）を `shipper` テーブルに突き合わせて存在確認できない。

その結果、`ShipperExistenceChecker` を Shipper のデータストアに対して実装できず、US04 の荷主存在確認（受入基準 1）と、IT2 の cargo テーブル `shipper_id`（ADR: ShipperId を業務識別子として保持）が成立しない。

## 決定

**Shipper Context は `ShipperId`（Guid）を `shipper.shipper_uuid` カラムに永続化し、Booking Context は `ShipperId`（Guid）を業務識別子として荷主を参照する。** `ShipperExistenceChecker` はこの `shipper_uuid` に対する存在確認として実装する。

### 変更箇所

1. **マイグレーション 0005**（両方言）: `shipper` テーブルに `shipper_uuid` カラムを追加する（forward-only・ADR-0003）。

   ```sql
   ALTER TABLE shipper ADD COLUMN shipper_uuid <TEXT|UUID>;
   ```

2. **ShipperRepository.save**: `ShipperId.value shipper.Id` を `shipper_uuid` に保存する。

3. **ShipperExistenceChecker アダプタ**: Booking Context の ACL 実装として、`shipper` テーブルを `shipper_uuid = @guid` で検索する生 SQL アダプタを `CargoTracker.Booking.Infrastructure` に置く（Shipper プロジェクトを参照せず、BC 分離を ArchUnitNET で維持する）。

4. **cargo.shipper_id**: 予約は `ShipperId`（Guid）を `cargo.shipper_id` に保持する（ADR-0007 と同様、Shipper のサロゲートキーへの物理 FK は張らない）。

### 代替案

- **shipper_code で参照する**（却下）: `ShipperCode` は Guid の先頭 8 文字のみで衝突可能性があり、かつ `Cargo.ShipperId`（Shared カーネル・Guid）をコード文字列へ変換するとドメインの識別子表現を変える必要が生じる。ドメイン全体（`CargoBooked` イベント・集約フィールド）への波及が大きい。
- **物理 FK（cargo.shipper_id → shipper.id）を張る**（却下）: BC 間で DB サロゲートキーを共有すると Booking が Shipper の物理スキーマに結合し、BC の自律性（ArchUnitNET で担保する分離）を損なう。DDD の「コンテキスト境界は識別子で参照する」原則にも反する。

## 影響

### ポジティブ

- 共有カーネルの `ShipperId`（Guid）が永続的な横断識別子となり、`ShipperExistenceChecker` ACL がデータストアに対して実装可能になる。
- Booking は Shipper の物理スキーマ（サロゲートキー）に結合せず、識別子（Guid）のみで参照する。BC 自律性を保つ。

### ネガティブ

- IT1 で確立した Shipper 永続化（`ShipperRepository.save`・`shipper` テーブル）に後方追加の変更が入る。既存のリポジトリテスト（インライン DDL）と受入テストのシードを追随させる必要がある。
- `shipper_uuid` は後方追加のため nullable で導入する（SQLite の `ALTER TABLE ADD COLUMN` 制約）。新規登録以降は常に設定されるが、アプリ層で NOT NULL を前提としない。

## コンプライアンス

- `ShipperRepository.save` 後に `shipper_uuid` が `ShipperId` の Guid 文字列で保存されることを統合テストで確認する。
- `ShipperExistenceChecker` アダプタが、存在する `ShipperId` に true、存在しない `ShipperId` に false を返すことを統合テストで確認する。
- Booking プロジェクトが Shipper プロジェクトを参照しないこと（BC 分離）を ArchUnitNET で継続確認する。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（垂直スライス・BC 分離）、ADR-0004（Donald 永続化）、ADR-0007（cargo の状態・識別子表現）、`docs/design/domain-model.md`（ShipperExistenceChecker ACL）、`docs/design/data-model.md`（shipper / cargo）、`docs/development/iteration_plan-2.md`（US04・タスク 1.3）。
