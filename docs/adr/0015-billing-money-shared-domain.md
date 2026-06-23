# 0015 Billing Context の Money を shared.domain.Money に一本化する

Billing Context 専用の `opaque type Money` (`billing.domain.model.valueobjects.Money`) を廃止し、Estimation や PricingService と共有の `cargotracker.shared.domain.Money` (`final case class`) に統一する。

日付: 2026-06-23

## ステータス

2026-06-23 承認 (IT7 タスク 0.4 にて適用済み)。

## コンテキスト

IT6 完了時点で Billing Context は独自の `Money` 値オブジェクトを保持していた。

| 場所 | 型 | API |
| :--- | :--- | :--- |
| `cargotracker.shared.domain.Money` | `final case class Money private (currency: String, amount: Long)` | `Money.jpy(amount)`、`+`、`times(factor)` |
| `cargotracker.billing.domain.model.valueobjects.Money` | `opaque type Money = Long` | `Money.unsafeFrom(value)`、`value`、`plus`、`minus`、`multiplyByRate(rate)` |

`BillingCommandService.generate` は `PricingService.calculateActual` から `shared.Money` を受け取り、その `amount` を `BillingMoney.unsafeFrom` で再ラップしてから `Invoice.issue` に渡していた。型システム上は隔離されているが、実際は **同一の概念（JPY 金額）を 2 種類の型で表現** しており、以下の問題があった。

1. **転送のたびに `.amount` ↔ `unsafeFrom` 変換が必要** で、IT7 0.9 で予定している `invoice_line_item` 永続化や ADR 0014 Snapshot 適用時にコピーが膨張する
2. `multiplyByRate` のような請求書用ロジックが `BillingMoney` 側にのみ存在し、Estimation や他の Pricing 用途で再利用できない
3. SonarQube 解析で「同一概念の 2 重定義」傾向として記録されており、新規開発者が両者の使い分けで迷う
4. Billing は MVP の段階で **単通貨 (JPY) しか扱わない** ことが Inception Deck で確定しており、`opaque type Long` で通貨情報を失うメリット (型隔離) より、`shared.Money` の構造（currency + amount）に揃えるデメリットの方が小さい

## 決定

`cargotracker.billing.domain.model.valueobjects.Money` を **削除** し、Billing Context の `Invoice`、`InvoiceRepository`、`BillingCommandService`、Twirl ビューはすべて `cargotracker.shared.domain.Money` を直接利用する。

`shared.domain.Money` に以下を追加する。

- `def unsafeFromJpy(amount: Long): Money` — 永続化からの復元用 (検証スキップ)
- 拡張メソッド `def multiplyByRate(rate: BigDecimal): Money` — HALF_UP 丸めで通貨を保持して乗算

JPY 単通貨を前提とし、将来多通貨化する場合は `currency` フィールドが既に存在するため拡張は安全。

## 検討した代替案

### 案 A: 現状維持 (両者を併存)

`BillingMoney` を残し、変換ボイラープレートで耐える。

- **メリット**: 既存コード無変更
- **却下理由**: ボイラープレート蓄積。IT7 0.9 `invoice_line_item` 永続化で `LineItem(name, Money)` 構造が増えると変換コストが線形に増える

### 案 B: Billing 専用 `JpyMoney` 型を新設 (opaque type)

`shared.Money` をラップする opaque type を Billing 側に置く。

- **メリット**: 型レベルで JPY のみ受け付けることを表明可能
- **却下理由**: opaque type は他コンテキストとの自動変換を妨げる。Estimation でも JPY しか扱わないため、Billing だけが特別な型を持つ理由がない

### 案 C: `shared.Money` を opaque type 化

`shared.Money` を `opaque type Money = Long` に変えて Billing と統一する。

- **却下理由**: 通貨情報を失う。Estimation の輸出ユースケース (将来の US 候補) で USD/EUR を扱えなくなる

## 帰結

### 正の帰結

- Billing Context の `Invoice.baseAmount / finalAmount` が `shared.Money` で表現され、PricingService からの直接渡しが可能に
- `multiplyByRate` extension が Estimation 等からも再利用可能
- BillingMoney を削除したことで `valueobjects` パッケージのファイル数が減り、レイヤー責務が明確化
- ADR 0014 Snapshot ADT (`Invoice.Snapshot`) の `Money` 型が shared 側に揃い、他集約への Snapshot 拡張が容易に

### 負の帰結

- `Money` の `.value: Long` (Billing 旧 API) を使っていた Twirl ビューを `.amount` に書き換える必要がある (`list.scala.html` / `detail.scala.html` の 4 箇所、IT7 0.4 で対応済)
- `shared.Money` は `case class` ベースで等値性比較が `currency == currency && amount == amount` になる。通貨が異なる場合の `+` は `CurrencyMismatch` を返す既存仕様で問題ない
- 将来 Billing で多通貨を扱う場合は `BillingShipperId.preferredCurrency` などの拡張が必要

### 中立の帰結

- DB スキーマは無変更 (`base_amount` / `final_amount` カラムは `BIGINT` のまま、JPY 円単位)
- マイグレーション不要

## 影響を受けるコード

- 削除: `apps/cargo-tracker/app/cargotracker/billing/domain/model/valueobjects/Money.scala`
- 変更: `Invoice.scala`、`BillingCommandService.scala`、`ScalikeJdbcInvoiceRepository.scala`、`InvoiceSpec.scala`、`list.scala.html`、`detail.scala.html`
- 追加: `shared/domain/Money.scala` に `unsafeFromJpy` + `multiplyByRate`
