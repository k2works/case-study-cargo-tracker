-- シミュレーション由来の精算書を、経理の一覧から外すための印（[ADR-030] 決定 3・IT15）。
--
-- billingms は自分の DB に荷主コードを持たないため、**発行時に bookingms が
-- 判断した由来を保存する**。後から変わらない値である。
--
-- 混ざると、発行済み精算書の一覧と支払期限超過の一覧に架空の未入金が積み上がる。
-- 督促の判断はそこで行われるため、実害がある。
--
-- **既存行は印を持たない。** 不変条件を後から足すと、列が無かったころの行が
-- 読めなくなる。既定値で埋めてから NOT NULL にする。
-- **IF NOT EXISTS は書かない。** 設計との突き合わせ（SchemaDesignConsistencyTest）が
-- IF を列名として読む。Flyway は同じ版を二度当てないので、要らない。
ALTER TABLE invoice ADD COLUMN simulated BOOLEAN;

UPDATE invoice SET simulated = FALSE WHERE simulated IS NULL;

ALTER TABLE invoice ALTER COLUMN simulated SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invoice_simulated ON invoice (simulated);
