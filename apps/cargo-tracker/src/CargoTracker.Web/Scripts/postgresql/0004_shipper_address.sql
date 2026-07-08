-- 荷主に住所カラムを追加（US02 受入条件・domain-model の Address VO）。
-- 任意項目のため NULL 許容。forward-only（既存 0002 は変更しない・ADR-0003）。
ALTER TABLE shipper ADD COLUMN address VARCHAR(500);
