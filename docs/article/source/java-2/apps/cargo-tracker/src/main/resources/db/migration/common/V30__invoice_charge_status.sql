-- 料金の状態と料金調整、荷主参照を精算書に足す（US21 / US22）。
--
-- **invoice テーブルは V1 で作成済みである。** 本 IT で足すのは 6 列だけであり、
-- 既存の列（金額・割引率・税率・支払状態）はそのまま使う。
-- **設計ドキュメントが定義済みのものを作り直さない。**

-- 荷主への参照（US22）。**割引の可否は荷主種別で決まる**ため、
-- どの荷主の請求書かを精算書自身が持つ。
-- **FK は張らない** — BC が違う（ADR-005 / ADR-012。booking_id と同じ扱い）。
-- **NULL 可で足す** — 既存の行は無いが、列を足すときに NOT NULL を課さないのは
-- 「新しい不変条件で既存の行を読めなくしない」という繰り返しの判断による。
ALTER TABLE invoice ADD COLUMN shipper_id UUID;

-- 料金の状態（ADR-017）。**payment_status を流用しない。**
-- 1 つにまとめると「料金は確定したが未入金」と「料金が未確定」が
-- 同じ PENDING になり、督促の対象を選べなくなる（US23 の受入基準）。
ALTER TABLE invoice ADD COLUMN charge_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE invoice ADD CONSTRAINT chk_invoice_charge_status
    CHECK (charge_status IN ('DRAFT', 'CONFIRMED'));

-- 料金調整（US21 の受入基準 6「料金調整（減額・補償費用）の入力ができる」）。
-- **invoice_line_item を使わない**（ADR-016）。明細行を要求する受入基準が無く、
-- 調整は 2 種類しかない。**種類が 3 つ以上に増えたら明細テーブルへ移す。**
ALTER TABLE invoice ADD COLUMN adjustment_reduction_value    INTEGER;
ALTER TABLE invoice ADD COLUMN adjustment_compensation_value INTEGER;
ALTER TABLE invoice ADD COLUMN adjustment_currency           VARCHAR(3);
ALTER TABLE invoice ADD COLUMN adjustment_reason             VARCHAR(200);

-- **調整があるなら理由がある。** 理由の無い調整は後から根拠を説明できない。
-- 3 列がそろって NULL か、そろって値を持つかのどちらかである。
ALTER TABLE invoice ADD CONSTRAINT chk_invoice_adjustment_paired
    CHECK (
        (adjustment_reduction_value IS NULL AND adjustment_compensation_value IS NULL
                                            AND adjustment_reason IS NULL)
     OR (adjustment_reduction_value IS NOT NULL AND adjustment_compensation_value IS NOT NULL
                                                AND adjustment_reason IS NOT NULL)
    );

-- **金額は負にできない。** 返金は精算の取り消しを伴う別の業務である
-- （release_scope.md のスコープ外）。
ALTER TABLE invoice ADD CONSTRAINT chk_invoice_amounts_not_negative
    CHECK (base_amount_value >= 0 AND tax_amount_value >= 0 AND total_amount_value >= 0);

-- 請求対象一覧の絞り込み（未請求・確定済み）。
CREATE INDEX idx_invoice_charge_status ON invoice (charge_status);

-- 精算書番号の採番。**アプリ側で MAX+1 を数えない** — 同時に 2 件発行すると衝突する
-- （tracking_number_seq / shipper_code_seq と同じ形）。
CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;
