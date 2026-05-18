-- tracking_number カラムを 20 → 25 文字に拡張
-- TRK-YYYYMMDD-XXXXXXXX = 4+1+8+1+8 = 22 文字のため余裕を持たせる
ALTER TABLE cargo_summary
    ALTER COLUMN tracking_number TYPE VARCHAR(25);
