-- V003: token_entry テーブル（Axon Framework 5.1 JDBC TokenStore 用）
--
-- PooledStreamingEventProcessor は Axon Server の Event Store からトークン経由で
-- イベントを読み出すため、各 Processor / Segment の処理進捗を JDBC に永続化する。
--
-- trackingms / handlingms 等と同方針（ADR-0009）。

CREATE TABLE IF NOT EXISTS token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment        INTEGER      NOT NULL,
    mask           INTEGER      NOT NULL,
    token          BYTEA,
    token_type     VARCHAR(255),
    timestamp      VARCHAR(255),
    owner          VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);
