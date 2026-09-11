-- 調整の明細をリプレイで積み上げない（IT13 T7 で実測）。
--
-- **追記専用の行はリプレイで増える**（IT6 の教訓）。算出の明細は消して入れ直す
-- ので増えないが、**調整は別のイベントで積む**ので入れ直せない。同じ調整イベントが
-- 2 度届くと（少なくとも 1 回配送・退避先からの処理し直し）、MAX(line_seq)+1 が
-- 新しい番号を採って同じ内容の行がもう 1 行できる。
--
-- **識別子を内容から導く。** 元イベントの識別子を持ち、一意にする。
ALTER TABLE invoice_line_item ADD COLUMN source_event_id VARCHAR(36);

CREATE UNIQUE INDEX uq_invoice_line_item_source
    ON invoice_line_item (source_event_id)
 WHERE source_event_id IS NOT NULL;
