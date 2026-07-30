-- IT7: CUSTOMS_HOLD 例外の申告番号（ADR-012 / IT7 計画 1.3）。
-- 通関留置は申告番号ごとに独立した例外として登録できる必要がある。
-- declaration_number を保持し、「未解決の同種 + 同一申告番号」を冪等キーとする
-- （DELAY/DAMAGE/LOST は従来どおり「未解決同種」で冪等）。

-- Up Migration

ALTER TABLE tracking_exception_event ADD COLUMN declaration_number VARCHAR(50);
