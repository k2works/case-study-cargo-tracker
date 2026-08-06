-- cargo（貨物予約・US04/US05/US06）。PostgreSQL 方言。
-- shipper_id は Shipper Context のサロゲートキーへの物理 FK ではなく、
-- ShipperId（Guid）を業務識別子として保持する（BC 分離・ACL 整合・IT2 設計判断）。
CREATE TABLE cargo (
    id                   BIGSERIAL PRIMARY KEY,
    booking_id           VARCHAR(20)   NOT NULL UNIQUE,
    shipper_id           UUID          NOT NULL,
    cargo_type           VARCHAR(30)   NOT NULL DEFAULT 'GENERAL',
    weight               NUMERIC(10,3) NOT NULL,
    origin_unlocode      VARCHAR(5)    NOT NULL,
    destination_unlocode VARCHAR(5)    NOT NULL,
    arrival_deadline     TEXT          NOT NULL,
    booking_status       VARCHAR(30)   NOT NULL DEFAULT 'PRELIMINARY',
    dimension_length     NUMERIC(10,3),
    dimension_width      NUMERIC(10,3),
    dimension_height     NUMERIC(10,3),
    quantity             INTEGER,
    description          VARCHAR(500),
    hazardous_class      VARCHAR(10),
    un_number            VARCHAR(10),
    proper_shipping_name VARCHAR(200),
    min_temperature      NUMERIC(10,3),
    max_temperature      NUMERIC(10,3),
    temperature_unit     VARCHAR(20),
    consignee_name       VARCHAR(200),
    consignee_address    VARCHAR(500),
    consignee_email      VARCHAR(200),
    created_at           TEXT NOT NULL DEFAULT (now())::text,
    updated_at           TEXT NOT NULL DEFAULT (now())::text,
    version              BIGINT        NOT NULL DEFAULT 0
);
