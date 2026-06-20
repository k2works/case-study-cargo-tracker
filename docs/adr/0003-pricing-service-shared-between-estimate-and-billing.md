# 0003 料金計算ドメインサービスを Estimation と Billing で共通化する

US01（輸送見積）と US21（輸送料金算出）の料金計算ロジックを単一のドメインサービスに集約する

日付: 2026-06-20

## ステータス

2026-06-20 提案されました

## コンテキスト

US01（IT1 で実装）と US21（IT6 で実装）はどちらも「輸送料金」を算出するが、ユースケース上の位置づけが異なる：

- **US01 見積**: 予約前に提示する**概算**料金（航海未確定、ルート候補から最良値）
- **US21 料金算出**: 確定経路に基づく**正式**料金（精算前提）

実業務では「見積金額と請求金額が異なる」は最大のクレーム源（リリース計画レビューで User Rep 指摘 M2）。同じ単価表・割引適用・通貨換算ロジックを別実装すれば、必ず乖離する。

選択肢：

| 案 | 配置 | 評価 |
|----|------|------|
| A. Estimation・Billing 双方に独立実装 | 各 Bounded Context | 関心の分離が明快だがロジック乖離リスク高 |
| B. 共有カーネル（Shared Domain）に PricingService 配置 | shared/pricing | 単一実装で両方から呼べる、変更影響が一点に集中 |
| C. Billing に PricingService を置き Estimation から ACL ポート経由で呼ぶ | Billing 主管 | 業務ルール所在が Billing として自然だが、IT1 で Billing が存在しない |

## 決定

**案 B: 共有カーネル `domain.shared.pricing.PricingService` を定義し、Estimation と Billing が同一インターフェースを利用する**。

詳細：

- 配置: `app/cargotracker/shared/domain/pricing/PricingService.scala`
- インターフェース（IT1 で固定し、以降変更しない）:
  ```scala
  trait PricingService:
    def estimateCost(
      origin: Location,
      destination: Location,
      cargoType: CargoType,
      weight: Weight,
      candidateVoyage: Option[VoyageNumber]
    ): Either[DomainError, Money]
  ```
- 実装は段階的に深化：
  - **IT1（US01）**: 直線距離 × 単価表（モック）+ 重量・貨物種別係数。航海未指定でも動作
  - **IT3（US07/US08）**: 確定航海のセグメント別単価を反映
  - **IT6（US21）**: 確定経路全レグの正式単価 + 燃料サーチャージ
  - **IT8（US22）**: 法人割引適用は呼び出し側（Billing）の責務とし、PricingService は base price のみ返す
- 単価表は `pricing_tariff` テーブル（IT3 で導入）から読む。IT1 はインメモリ固定値

## 影響

**ポジティブ**:

- 見積と精算の乖離が発生しない（同じ関数を呼ぶ）
- 単価変更時の修正箇所が 1 箇所
- 単体テストが集約され、料金計算の境界値テストが充実する
- 将来の通貨換算・複数通貨対応も単一実装で導入可能

**ネガティブ・トレードオフ**:

- 共有カーネルが肥大化するリスク → `pricing` パッケージに閉じ、`shared.pricing` 配下のみ共有とする
- IT3/IT6 でインターフェースを変更したくなったら、Estimation の引数を増やすのではなく **`PricingContext` 値オブジェクトを介して拡張**する（インターフェース後方互換性を維持）
- 法人割引（US22）は Billing 固有のため、PricingService の外で適用（責務分離）

## コンプライアンス

- ArchUnit ルール: `Estimation Context` と `Billing Context` の両方が `shared.pricing.PricingService` を依存先に持つこと
- 単体テスト: 同一入力に対し US01 のコントローラと US21 のコントローラが同一の base price を返すことを確認（IT6 で追加）
- PricingService の変更は ADR 追補が必要

## 備考

- 著者: AI Agent
- 関連: US01、US21、US22、ADR 0001
- 参考: リリース計画レビュー（2026-06-20）M2 指摘
