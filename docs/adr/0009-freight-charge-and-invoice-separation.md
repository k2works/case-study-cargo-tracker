# ADR 0009: 輸送料金（FreightCharge）と精算書（Invoice）の段階分割（IT7）

## ステータス

承認（IT7 時点）

## コンテキスト

IT7 の US21（輸送料金を算出する）・US22（法人割引を適用する）で、引取済予約に対する輸送料金の算出・確定を実装する。後続の US23（精算を処理する・IT8）は「確定した輸送料金をもとに精算書（invoice）を発行し、荷主通知・入金確認・精算完了処理を行う」。

IT6 時点の Billing Context データモデルには `invoice`・`invoice_line_item`・`payment` の 3 テーブルしか無く、US21 の受入基準「輸送料金が『確定』状態で登録される」と US23 の「確定状態の輸送料金をもとに精算書を発行」を素直に表現する構造が欠けていた。

選択肢は次の 2 つであった。

1. **invoice を DRAFT 状態から使い回す**: US21 で invoice を DRAFT で作り、US23 で発行（invoice_number 採番・issued_at）に進める。テーブルは増えないが、「料金算出」と「精算書発行」という異なる業務段階・異なるアクター責務が 1 テーブルに混在し、状態機械が肥大化する。
2. **freight_charge を新設して段階分割する**: 「確定した輸送料金」を `freight_charge` として独立させ、US23 の精算書生成の入力とする。テーブルは増えるが、業務段階（料金確定 → 精算書発行）が構造で表現される。

## 決定

**選択肢 2 を採用し、`freight_charge`／`freight_charge_adjustment` を新設する。**

- `freight_charge` は集約ルート `FreightCharge`（基本料金＋例外調整＋法人割引で total を導出、`ChargeStatus` DRAFT/CONFIRMED）に対応する。予約 1 件に 1 料金（`booking_id` UNIQUE・二重算出防止・冪等 upsert）。
- US23（IT8）は確定した `freight_charge` を入力に `invoice` を生成する。`invoice` は精算書発行・支払管理の責務に専念する。
- 料金算出（経理担当者 `ROLE_BILLING`）は「輸送料金の確定」までを担い、精算書発行と明確に段階分離する。

## 影響

- US21/US22 の受入基準（確定状態での登録・割引根拠の保持）が `freight_charge` の状態・カラムで素直に表現される。
- US23（IT8）は `freight_charge` → `invoice` の変換として実装でき、料金確定と精算書発行の責務境界が明確になる。
- Billing Context のテーブルが 3 → 5 に増える。data-model.md に反映済み。
- `Money`・`DiscountRate` の BC ローカル定義方針は [ADR-0010](0010-billing-money-value-object.md) を参照。
