-- V002: token_entry テーブル（Axon Framework 5.1 JDBC TokenStore 用）
--
-- PooledStreamingEventProcessor は Axon Server の Event Store からトークン経由で
-- イベントを読み出すため、各 Processor / Segment の処理進捗を JDBC に永続化する。
--
-- 命名規則:
--   Axon デフォルトの "TokenEntry" / "processorName" 等のキャメルケースは PostgreSQL では
--   ダブルクォート必須になり運用上ハマりやすいため、snake_case に統一する（ADR-0009）。
--   AxonTokenStoreConfig#tokenSchema() の TokenSchema 定義と必ず一致させること。
--
-- 設計準拠: ADR-0008 IT3 課題（pooled-streaming への復帰）と ADR-0009（connector 明示依存）

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
