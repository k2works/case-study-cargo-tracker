-- 書けなかったイベントの退避先（Dead Letter Queue）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「Axon 管理テーブル」
-- 判断: docs/adr/cargo-tracker/0014-poison-events-are-parked-not-blocking.md
--
-- **1 件で全部止まるのを避けるために置く。** IT11 では誤配の自動起票が桁あふれで
-- 落ち、その 1 件で Event Processor が止まって後続のイベントが全部届かなくなった。
-- 層ごとの検査はすべて緑のままで、クラスタ E2E だけが気づいた。
--
-- 列名は Axon の既定（camelCase の DeadLetterEntry）ではなく、この DB の書き方に
-- 合わせている（AxonJdbcConfiguration#deadLetterSchema で明示する）。1 つだけ別の
-- 書き方が混ざると、運用で表を引く人が探せない。
--
-- timestamp は Axon の既定の列名だが、型名と同じ語なので event_timestamp にする。

CREATE TABLE dead_letter_entry (
    dead_letter_id       VARCHAR(255)  NOT NULL,
    processing_group     VARCHAR(255)  NOT NULL,
    sequence_identifier  VARCHAR(255)  NOT NULL,
    sequence_index       BIGINT        NOT NULL,
    event_type           VARCHAR(255)  NOT NULL,
    event_identifier     VARCHAR(255)  NOT NULL,
    type                 VARCHAR(255)  NOT NULL,
    event_timestamp      VARCHAR(255)  NOT NULL,
    payload              BYTEA         NOT NULL,
    metadata             BYTEA,
    aggregate_type       VARCHAR(255),
    aggregate_identifier VARCHAR(255),
    sequence_number      BIGINT,
    token_type           VARCHAR(255),
    token                BYTEA,
    enqueued_at          VARCHAR(255)  NOT NULL,
    last_touched         VARCHAR(255),
    processing_started   VARCHAR(255),
    cause_type           VARCHAR(255),
    cause_message        VARCHAR(1023),
    diagnostics          BYTEA,
    CONSTRAINT pk_dead_letter_entry PRIMARY KEY (dead_letter_id),
    CONSTRAINT uk_dead_letter_entry_sequence
        UNIQUE (processing_group, sequence_identifier, sequence_index)
);

CREATE INDEX idx_dead_letter_entry_processing_group
    ON dead_letter_entry (processing_group);

CREATE INDEX idx_dead_letter_entry_sequence_identifier
    ON dead_letter_entry (processing_group, sequence_identifier);
