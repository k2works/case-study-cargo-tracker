-- 要確認一覧（S70）からシミュレーション由来を外す（US33 §受入基準 3 / [ADR-0020] 決定 4）。
--
-- 正典: docs/design/cargo-tracker/data-model.md（billing_read_db）
--
-- billingms の要確認は請求（INVOICE）と予約（BOOKING）を指す。どちらも
-- 由来の印を持つ表から引けるので、登録のときに解決して列に書く。
ALTER TABLE attention_item
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN attention_item.simulated IS
    'シミュレーション由来か。要確認一覧（S70）は既定で外す。登録時に対象から解決する（ADR-0020 決定 4）';

CREATE INDEX ix_attention_item_simulated ON attention_item (simulated);
