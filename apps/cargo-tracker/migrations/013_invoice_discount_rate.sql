-- IT7 クローズ内対応（programmer#1 High）: 割引率を明示保持する。
-- 従来は discount_amount / base_amount から割引率を逆算していたが、整数丸めで 0.3 を超え
-- DiscountRate.of が throw する（base が小さい法人請求で復元不能）ため、割引率をそのまま永続化する。
-- 出典: docs/development/review/iteration-7*（programmer#1）、集約状態の再導出禁止。

-- Up Migration

ALTER TABLE invoice
    ADD COLUMN discount_rate NUMERIC(5,4) NOT NULL DEFAULT 0;
