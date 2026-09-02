-- 入金と取り消し（US23・[ADR-028] 決定 2・3）。

-- 入金の記録。**請求書の属性ではない**（決定 2）——請求書に起きた別の出来事であり、
-- invoice の行を書き換えずに残す（発行した請求書の金額は動かない）。
-- 分割入金は本 IT では扱わないが、列に持つとそれは列の増設になる。
CREATE TABLE payment (
    id                    BIGSERIAL PRIMARY KEY,
    invoice_id            BIGINT        NOT NULL REFERENCES invoice (id),
    -- invoice 側に揃える（論理モデルの INTEGER から変更・注 6）
    paid_amount_value     NUMERIC(15,2) NOT NULL,
    paid_amount_currency  VARCHAR(3)    NOT NULL DEFAULT 'JPY',
    -- 入金日。**日付である**——通帳に時刻は無い
    paid_at               DATE          NOT NULL,
    payment_method        VARCHAR(30)   NOT NULL,
    transaction_reference VARCHAR(100),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_invoice ON payment (invoice_id);

-- 取り消し（赤伝・決定 3）。**消さずに残す**——DB を直すのは監査に耐えない。
-- 支払いの状態には混ぜない（決定 4）。
ALTER TABLE invoice ADD COLUMN voided_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE invoice ADD COLUMN void_reason VARCHAR(200);

-- 取り消したあと、同じ予約に出し直せるようにする（決定 3）。
--
-- **部分 UNIQUE は使わない。**「取り消し済みを除いて一意」を
-- `CREATE UNIQUE INDEX ... WHERE voided_at IS NULL` で書くと、**H2 が構文を解釈できない**
-- （実測済み）。CI（PostgreSQL）は緑のまま、ローカル起動だけが落ちる。
--
-- かわりに印を持つ。有効な請求書は空文字、取り消した請求書は請求番号（一意）を入れる。
-- (booking_id, void_marker) の素の UNIQUE で「有効な請求書は予約ごとに 1 通」を守れる。
ALTER TABLE invoice ADD COLUMN void_marker VARCHAR(30) NOT NULL DEFAULT '';
