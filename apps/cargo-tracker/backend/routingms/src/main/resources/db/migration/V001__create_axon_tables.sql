-- Axon 管理テーブル。
--
-- 正典: docs/design/cargo-tracker/data-model.md「Axon 管理テーブル」
--
-- 列名は Axon 5 の JdbcTokenStore の既定に合わせる（TokenSchema で明示する）。
-- saga_entry / association_value_entry は作らない。Axon 5 に Saga が無いため
-- （ADR-0001 決定 6。IT1 スパイクで jar に 1 クラスも無いことを確認済み）。
--
-- mask は NOT NULL。無いと起動時に落ちる（take-4 の実測スキーマ）。

CREATE TABLE token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment        INTEGER      NOT NULL,
    token          BYTEA,
    token_type     VARCHAR(255),
    timestamp      VARCHAR(255),
    owner          VARCHAR(255),
    mask           INTEGER      NOT NULL,
    PRIMARY KEY (processor_name, segment)
);

CREATE INDEX idx_token_entry_processor_name ON token_entry (processor_name);
