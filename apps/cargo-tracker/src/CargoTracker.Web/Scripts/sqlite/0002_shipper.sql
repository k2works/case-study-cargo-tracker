-- 荷主テーブル（SQLite 方言・US02/US03・data-model）
-- 個人/法人を shipper_type で判別する単一テーブル。法人のみ契約番号・割引率を保持。
-- email の一意性はアプリケーション層で担保する（domain-model 規則 2）。
CREATE TABLE shipper (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    shipper_code    TEXT    NOT NULL,
    shipper_type    TEXT    NOT NULL,
    name            TEXT    NOT NULL,
    email           TEXT    NOT NULL,
    phone           TEXT,
    contract_number TEXT,
    -- discount_rate は TEXT アフィニティで保持する。NUMERIC だと 0→INTEGER・0.15→REAL と
    -- 行ごとに CLR 型が変わり Dapper のマテリアライザキャッシュが破綻するため（ADR-0003 二方言差異）。
    discount_rate   TEXT    NOT NULL DEFAULT '0',
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipper_code UNIQUE (shipper_code)
);
