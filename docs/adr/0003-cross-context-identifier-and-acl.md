# ADR 0003: BC 越境識別子と ACL 実装方式・Packwerk privacy

Booking→Shipper の境界を確立するにあたり、越境識別子を `shippers.id`（サロゲートキー）に統一し、ACL をモジュラモノリス内のインプロセス・アダプタで実装し、Packwerk privacy でパックの公開面を強制する。

日付: 2026-07-28

## ステータス

2026-07-28 承認されました（IT2）

## コンテキスト

- IT2 で Booking Context（貨物予約）を実装するにあたり、Booking は荷主の存在確認を行う必要がある。ADR-0001 で Shipper は独立コンテキストとし「Booking からは ACL 経由参照」と決めたが、**越境識別子・ACL の実装方式・パックの公開面**が未確定だった。
- **識別子の二重定義**: domain-model.md には Booking 文脈の `ShipperId <<value object>>（id: String, shipperType: ShipperType）` と Shared Kernel の `ShipperId（id: UUID）` の 2 つの `ShipperId` 定義があり、一方 data-model.md の `cargos.shipper_id` は `bigint FK→shippers.id`（サロゲート）で、実装は 3 者不一致だった。放置すると BC 間で識別子の変換層が二重化する（IT1 ふりかえり Try T4）。
- **ACL 実装方式**: architecture_backend.md は ACL を Faraday/HTTP の Secondary Adapter 前提で記述しているが、現状は単一 Rails アプリ（モジュラモノリス）であり、コンテキスト間 HTTP 通信は過剰なオーバーヘッドとなる。
- **境界の強制**: Packwerk は `enforce_dependencies` のみ有効で `enforce_privacy` は未設定のため、パックの内部集約が無制限に他パック・アプリから参照可能で、公開面が定義されていなかった（Try T3）。

## 決定

1. **越境識別子は `shippers.id`（bigint サロゲートキー）を正本**とする。BC 間で荷主を参照する際は `shippers.id` を用い、`cargos.shipper_id` FK と整合させる。`ShipperCode`（SHP-XXXXXXXX）は Shipper Context 内の業務識別子として維持し、越境には用いない。domain-model.md の 2 つの `ShipperId` 定義は削除し、越境識別子は `shippers.id` である旨に一本化する（UUID 記述は撤回）。

2. **ACL はモジュラモノリス内のインプロセス・アダプタで実装**する。Booking のドメイン層に `ShipperExistenceChecker` 出力ポート（`exists?(shipper_id)`）を定義し、Booking のインフラ層のアダプタが Shipper Context の**公開 API**を呼び出す。HTTP は用いない。将来コンテキストを別サービスに分離する場合は、アダプタを Faraday/HTTP 実装へ差し替える（ポートは不変）。

3. **Shipper Context は公開 API を `public/` に定義**する。
   - `Shipper::Public::ShipperDirectory`: `exists?(id)` / `find(id)` / `all` — 他コンテキスト・アプリ層向けの参照 API。
   - `Shipper::Public::ShipperRegistration`: 荷主登録のファサード（合成ルートの簡素化）。
   - 集約・値オブジェクト・リポジトリ実装・アプリケーションサービスは非公開（`public/` 外）とする。

4. **Packwerk `enforce_privacy: true` を Shipper / Booking パックで有効化**する（`packwerk-extensions` の privacy checker を利用）。パック外からの参照は `public/` の公開定数のみに限定し、内部集約への直接参照を静的に禁止する。Booking パックは `packs/shared` と `packs/shipper`（公開面のみ）に依存し、Shipper の内部へは到達できない。

5. **DB レベルの越境参照は当面 物理外部キー（`cargos.shipper_id` → `shippers.id`）で整合性を担保**する。モジュラモノリス段階では参照整合性の担保を優先する意図的な選択であり、将来コンテキストを別サービス／別スキーマに分離する際は、この FK を落として論理参照（FK なし + ACL 存在確認）へ移行する（本 ADR を改訂）。アプリ層は既に ACL で疎結合化されているため、この移行はアダプタと FK 定義の変更に閉じる。

## 影響

- `packs/shipper/app/public/` に公開 API（ShipperDirectory・ShipperRegistration）を追加。`ShippersController`（アプリ層）と Booking の ACL アダプタは公開 API 経由で Shipper を利用する。
- `packs/shipper/package.yml`・`packs/booking/package.yml` に `enforce_privacy: true` と依存宣言を追加。
- domain-model.md の `ShipperId` 記述を `shippers.id` 越境識別子に改訂（Booking 文脈・Shared Kernel の二重定義を解消）。
- architecture_backend.md の ACL（HTTP 前提）に対し、IT2 はインプロセス実装である旨を補足。将来のサービス分離時に本 ADR を再検討する。
