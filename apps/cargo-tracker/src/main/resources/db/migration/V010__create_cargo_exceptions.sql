-- V010: 貨物例外テーブルの作成
CREATE TABLE cargo_exceptions (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    tracking_number   VARCHAR(50)  NOT NULL,
    exception_type    VARCHAR(20)  NOT NULL,
    location_code     VARCHAR(10),
    occurred_at       TIMESTAMP    NOT NULL,
    reason            VARCHAR(500),
    urgent            BOOLEAN      NOT NULL DEFAULT FALSE,
    resolution        VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cargo_exceptions_tracking_number ON cargo_exceptions (tracking_number);
