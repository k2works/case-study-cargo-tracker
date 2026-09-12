-- 入金の記録（US23 §受入基準 3・4）。正典の ER にはあったが実体が無かった（注 N3）。
--
-- **payment_id を PK にする**（= UNIQUE）。追記系投影は元イベントの識別子を
-- UNIQUE にする（data-model.md:43）。少なくとも 1 回配送の再配送で、同じ行が
-- 二度入らない。
--
-- **投影ではなく追記専用の記録**であり、リプレイで TRUNCATE しない。
CREATE TABLE payment (
    payment_id  VARCHAR(36)   PRIMARY KEY,
    invoice_id  VARCHAR(36)   NOT NULL,
    amount      NUMERIC(14,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL,
    -- **入金のあった時刻**（記録した時刻ではない）。記録は後日になることがある。
    paid_at     TIMESTAMPTZ   NOT NULL,
    recorded_by VARCHAR(50)   NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id) ON DELETE CASCADE
);

CREATE INDEX idx_payment_invoice ON payment (invoice_id);

-- 発行の通知（US23 §受入基準 2）。
--
-- **送信基盤はスコープ外**（計画の注 N9。US19 §3 で確立した扱い）。残すのは
-- 「いつ・誰に・何を伝えたか」で、荷主は S62 で自社の請求書を読む。
-- **「連携した」と書ける実体が無いのに済ませない**——記録だけは残す。
--
-- **invoice_id を PK にする。** 発行は請求書 1 通につき 1 回（二度発行できない）
-- ので、少なくとも 1 回配送で同じ行が二度入らない。
CREATE TABLE invoice_notification (
    invoice_id  VARCHAR(36)  PRIMARY KEY,
    shipper_id  VARCHAR(36)  NOT NULL,
    kind        VARCHAR(30)  NOT NULL,
    notified_at TIMESTAMPTZ  NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoice (invoice_id) ON DELETE CASCADE
);

CREATE INDEX idx_invoice_notification_shipper ON invoice_notification (shipper_id);
