-- US05 冷凍・冷蔵貨物予約のための温度管理カラム追加
-- 危険物カラムは V4 で既に追加済み。本マイグレーションは冷凍系のみ。

ALTER TABLE cargo
    ADD COLUMN refrigeration_min_temp INTEGER,
    ADD COLUMN refrigeration_max_temp INTEGER,
    ADD COLUMN refrigeration_unit     VARCHAR(10);

-- データモデル不変条件: 3 カラムは「全て NULL」または「全て NOT NULL」のいずれか
ALTER TABLE cargo
    ADD CONSTRAINT cargo_refrigeration_all_or_none
    CHECK (
        (refrigeration_min_temp IS NULL AND refrigeration_max_temp IS NULL AND refrigeration_unit IS NULL)
     OR (refrigeration_min_temp IS NOT NULL AND refrigeration_max_temp IS NOT NULL AND refrigeration_unit IS NOT NULL)
    );
