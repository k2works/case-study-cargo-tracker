-- 貨物仕様のうち、寸法・個数・品名のカラムを追加する（US04 の受入基準）。
--
-- V1 は cargo テーブルを作成したが、これらのカラムを作っていなかった。
-- data-model.md には記載があったため、テーブルの存在だけを見ると揃っていると
-- 誤認する状態だった（IT2 計画時の突合で発覚）。
--
-- いずれも NULL 許容である。domain-model.md がオプション項目と定めており、
-- 重量だけが分かっていて寸法は未計測、という予約は業務上ありふれている。

ALTER TABLE cargo ADD COLUMN dimension_length NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN dimension_width  NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN dimension_height NUMERIC(10, 3);
ALTER TABLE cargo ADD COLUMN quantity         INTEGER;
ALTER TABLE cargo ADD COLUMN description      VARCHAR(500);

-- ドメインの不変条件を DB でも守る。**アプリだけに置くと、SQL で直接入れた
-- データや将来の別経路からの書き込みで破られる。**
ALTER TABLE cargo ADD CONSTRAINT chk_cargo_dimension_length
    CHECK (dimension_length IS NULL OR dimension_length > 0);
ALTER TABLE cargo ADD CONSTRAINT chk_cargo_dimension_width
    CHECK (dimension_width IS NULL OR dimension_width > 0);
ALTER TABLE cargo ADD CONSTRAINT chk_cargo_dimension_height
    CHECK (dimension_height IS NULL OR dimension_height > 0);
ALTER TABLE cargo ADD CONSTRAINT chk_cargo_quantity
    CHECK (quantity IS NULL OR quantity >= 1);

-- 寸法は 3 辺すべてが入っているか、すべて未入力かのどちらかである。
-- 一部だけ入っている状態は入力の取りこぼしであり、寸法として保存してはならない
-- （Dimensions.ofNullableCentimeters と同じ規則）。
ALTER TABLE cargo ADD CONSTRAINT chk_cargo_dimensions_all_or_none
    CHECK (
        (dimension_length IS NULL AND dimension_width IS NULL AND dimension_height IS NULL)
        OR (dimension_length IS NOT NULL AND dimension_width IS NOT NULL
            AND dimension_height IS NOT NULL)
    );
