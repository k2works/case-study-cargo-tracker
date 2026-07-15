-- cargo（貨物予約・US04/US05/US06）。SQLite 方言。
-- shipper_id は Shipper Context のサロゲートキーへの物理 FK ではなく、
-- ShipperId（Guid）を業務識別子として保持する（BC 分離・ACL 整合・IT2 設計判断）。
CREATE TABLE cargo (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_id           TEXT    NOT NULL UNIQUE,
    shipper_id           TEXT    NOT NULL,
    cargo_type           TEXT    NOT NULL DEFAULT 'GENERAL',
    weight               NUMERIC NOT NULL,
    origin_unlocode      TEXT    NOT NULL,
    destination_unlocode TEXT    NOT NULL,
    arrival_deadline     TEXT    NOT NULL,
    booking_status       TEXT    NOT NULL DEFAULT 'PRELIMINARY',
    dimension_length     NUMERIC,
    dimension_width      NUMERIC,
    dimension_height     NUMERIC,
    quantity             INTEGER,
    description          TEXT,
    hazardous_class      TEXT,
    un_number            TEXT,
    proper_shipping_name TEXT,
    min_temperature      NUMERIC,
    max_temperature      NUMERIC,
    temperature_unit     TEXT,
    consignee_name       TEXT,
    consignee_address    TEXT,
    consignee_email      TEXT,
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL,
    version              INTEGER NOT NULL DEFAULT 0
);
