-- 危険物・温度管理情報カラムの追加
ALTER TABLE cargo ADD COLUMN hazmat_un_code      VARCHAR(20);
ALTER TABLE cargo ADD COLUMN hazmat_hazard_class VARCHAR(50);
ALTER TABLE cargo ADD COLUMN hazmat_packing_group VARCHAR(10);
ALTER TABLE cargo ADD COLUMN temp_min_celsius    NUMERIC(5,2);
ALTER TABLE cargo ADD COLUMN temp_max_celsius    NUMERIC(5,2);
ALTER TABLE cargo ADD COLUMN temp_unit           VARCHAR(10);
