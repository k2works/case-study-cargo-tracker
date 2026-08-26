-- 地域区分と輸出免税（[ADR-027] 決定 1・8 の改訂・IT12）。

-- 区間係数（区間ごとの地域係数の合計）と、旅程で最も重い地域区分。
-- **発行時に確定した値を保存する**（決定 4）。区間の並びは持たない——
-- 発行後は再計算しないため、係数と区分があれば根拠を出せる。
ALTER TABLE invoice ADD COLUMN leg_factor NUMERIC(8,2);
ALTER TABLE invoice ADD COLUMN leg_region VARCHAR(20);

-- 既存行を埋めてから NOT NULL にする。IT11 に発行した請求書は区間数を
-- そのまま係数にしていた（すべて国内 1.0 と同じ扱い）。
UPDATE invoice SET leg_factor = leg_count WHERE leg_factor IS NULL;
ALTER TABLE invoice ALTER COLUMN leg_factor SET NOT NULL;

-- 輸出免税（決定 8 の改訂）。**判定の結果を保存する**——発行した請求書の税額は
-- 動かないため、あとから国コードを引き直して再判定しない。
-- 既定は課税。IT11 の請求書はすべて 10% で発行している。
ALTER TABLE invoice ADD COLUMN tax_exempt BOOLEAN NOT NULL DEFAULT FALSE;
