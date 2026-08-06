-- Booking Context: 特殊貨物（危険物・冷凍貨物）の追加情報（US05）。
-- 貨物種別に応じて該当列のみ設定される（DB 上は nullable、必須性はドメイン不変条件で担保）。
ALTER TABLE cargo
    ADD COLUMN hazardous_class      VARCHAR(10),
    ADD COLUMN un_number            VARCHAR(10),
    ADD COLUMN proper_shipping_name VARCHAR(200),
    ADD COLUMN min_temperature      NUMERIC(10, 3),
    ADD COLUMN max_temperature      NUMERIC(10, 3),
    ADD COLUMN temperature_unit     VARCHAR(20);
