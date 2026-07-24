# ADR 0010: 金額表現（Money）の BC ローカル定義と割引率の重複定義（IT7）

## ステータス

承認（IT7 時点）

## コンテキスト

IT7 の Billing Context 実装で金額（`Money`）と割引率（`DiscountRate`）を導入した。金額は法的リスクを伴うため浮動小数点を避け `rust_decimal::Decimal` ＋通貨コードで厳密に扱う（テスト戦略 5 章）。

`DiscountRate`（0.0000〜0.3000）は既に Shipper Context（`domain-shipper`）に契約割引率として存在する。Billing でも同じ値域の割引率を扱うため、「共有カーネル（shared-kernel）へ昇格して 1 型に統一するか、コンテキストごとに別型として定義するか」が論点となった。

これは IT6・ADR-0007 で確立した BC 独立方針（コンテキスト固有型は共有せず、対応関係は ACL の責務）と同じ判断軸である。

## 決定

### 1. `Money`・`DiscountRate` は shared-kernel へ昇格せず、Billing ローカル（`domain-billing`）に定義する

- `Money`（`Decimal` ＋ `Currency`）は `domain-billing` に定義する。加減算は通貨不一致時に `BillingError::CurrencyMismatch` を返す `Result` とする。
- **丸め規則（JPY）**: JPY は最小単位が 1 円のため、割合乗算（割引額）・基本料金算定の結果は円未満を四捨五入（`Decimal::round_dp(0)`・`MidpointAwayFromZero`）する。`Money::rounded()`／`multiply_ratio()` で丸めを一元化し、割り切れない料率（例: 15% × 端数金額）でも金額が円単位に収束することを単体テストで固定する。
- `DiscountRate`（0.0000〜0.3000）は Shipper の `DiscountRate` とは**別型**として `domain-billing` に再定義する。値域バリデーションは各 BC が自前で持つ。

### 2. BC 間の受け渡しはプリミティブ（Decimal）で行う

法人割引率は app 層の ACL（`ShipperDiscountProvider`）が Shipper から `Decimal` として取得し、Billing 側で自前の `DiscountRate` を構築する。`domain-billing` は `domain-shipper` に依存しない（Cargo.toml で強制）。

## 根拠

- **BC 独立の一貫性**: ADR-0007 で「コンテキスト固有型は共有しない・対応は ACL の責務」を確立済み。`Money`/`DiscountRate` を共有カーネルに置くと、Billing の金額仕様変更が全コンテキストの再コンパイル・共有カーネル肥大化を招く。
- **金額仕様の局所性**: 通貨・丸め・割引の仕様は Billing の関心事であり、他コンテキストは金額演算を必要としない。共有する必然性がない。
- **型の取り違え防止**: Shipper の割引率と Billing の割引率を別型にすることで、片方の値域変更が他方に暗黙に波及しない。

## 影響

- `domain-billing` は `shared-kernel` のみに依存し、他 BC の domain クレートに依存しない（BC 独立を Cargo.toml で強制）。
- 将来、複数コンテキストで金額演算が真に共通化した場合は、その時点で共有カーネル昇格を再検討する（YAGNI）。
- 金額計算のリグレッションは `domain-billing`・`app-billing` の単体テスト（名前付き定数の基本料金・割引・調整）で固定する（IT6 Try 教訓）。
