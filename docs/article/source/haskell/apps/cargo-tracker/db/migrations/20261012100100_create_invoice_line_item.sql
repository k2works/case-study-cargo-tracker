-- migrate:up

-- IT8 US23 精算処理 - 精算明細テーブル (data-model.md §invoice_line_item)
--
-- 基本料金・割引・税の内訳行を保持する。invoice.id へのサロゲート FK
-- (同一 BC 内のため FK 制約あり、BC 間疎結合ルールの対象外)。

CREATE TABLE invoice_line_item (
    id               BIGSERIAL PRIMARY KEY,
    invoice_id       BIGINT       NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    description      VARCHAR(200) NOT NULL,
    amount_value     BIGINT       NOT NULL,
    amount_currency  VARCHAR(3)   NOT NULL,
    seq_number       INTEGER      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ili_seq_unique UNIQUE (invoice_id, seq_number)
);

-- migrate:down

DROP TABLE invoice_line_item;
