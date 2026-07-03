# 0015 法人契約割引率の contract_rank 由来決定

法人割引率 (US22) を `shipper.discount_rate` 列で永続化せず、
`shipper.contract_rank` (Bronze/Silver/Gold) から Domain 関数で決定する

日付: 2026-07-03

## ステータス

採用 (2026-07-03、IT7 Ralph Loop iteration 5-6 で実装完了)

`Shipper.discountPercentage :: Shipper -> Integer` を 641b71d434e で実装、
`resolveDiscountPercentageByShipperId :: ShipperRepository m -> Text -> m (Either DomainError Integer)`
を Cross-BC helper として 3f635748 で追加済。

## コンテキスト

iteration_plan-7.md タスク 6.3 と data-model.md §shipper.discount_rate は
`shipper.discount_rate NUMERIC(5,4) NOT NULL DEFAULT 0` カラムの追加を計画していた。
一方、既存の `shipper.contract_rank` カラム (`Bronze` / `Silver` / `Gold`)
は法人契約のグレードを表現しており、割引率は契約グレードに対応する固定値を
使う運用が想定されている (要件定義書 US22 受入基準)。

`discount_rate` を独立カラムとして追加する場合、以下の選択肢がある。

- (A) contract_rank-derived: 契約ランクから Domain 関数で割引率を決定
  (Individual=0% / Bronze=5% / Silver=10% / Gold=15%)
- (B) 独立カラム保持: `discount_rate NUMERIC(5,4)` を追加し個別に管理

## 決定

**(A) contract_rank-derived を採用する。**

- Domain 関数 `discountPercentage :: Shipper -> Integer` を Single Source of
  Truth とし、`contract_rank` から Integer 百分率 (0-100) を導出する
- DB スキーマの変更 (migration 追加) は行わない
- Cross-BC 経由の割引解決は `resolveDiscountPercentageByShipperId` が担う
  (ADR-0004 Rule 4 準拠、Text-DTO 経由で Pricing BC 型を漏らさない)

## 結果

- **良**:
  - スキーマがシンプル (追加カラム 0)、migration 不要
  - `contract_rank` を Single Source of Truth として整合性が保証される
    (`contract_rank = Gold` かつ `discount_rate = 0.05` のような不整合が発生し得ない)
  - Domain モデルがビジネスルール (「Gold は 15%」) を直接表現、
    要件定義書 US22 受入基準と Domain コードが 1:1 対応
- **悪**:
  - 個別 shipper への割引率上書き (「特別優遇 20%」等) は現状不可
  - 将来の要件変更 (割引率をキャンペーン期間中に変える等) が発生した場合、
    (B) 案の再検討が必要
- **補**: プロモーション割引 (期間限定) は `Pricing/Domain/Model/Value/Discount.hs`
  の `Discount` VO が既にサポートしており、CalculateShippingCostCommand の
  `inputDiscount` に法人割引と加算・上書き適用する形で拡張余地がある。
  法人契約割引と一般割引を層別化する Discount 層構造は将来 ADR で検討する

## 影響範囲

- data-model.md §shipper.discount_rate: 記述削除または「将来の拡張案」への降格
  (IT7 内で反映)
- iteration_plan-7.md タスク 6.3 (Postgres migration): 不要としてマーク

## 参照

- iteration_plan-7.md タスク 6.1-6.5 (US22 法人割引)
- ADR-0004 (Cross-BC Shipper 参照)
- ADR-0012 (トランザクション境界と Cross-BC 参照ポリシー)
- b71d434e `feat(shipper): US22 discountPercentage を Shipper 集約に追加`
- 3f635748 `feat(shipper): US22 resolveDiscountPercentageByShipperId Cross-BC helper 追加`
