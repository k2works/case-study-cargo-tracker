-- 通関申告の現在状態（US29 / IT12）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「customs_declaration」
--
-- **追記専用の履歴テーブルは作らない。** 状態変更の履歴は Event Store が持つ
-- （data-model.md:656）。ここは現在状態だけで、画面の履歴はイベント列から読む。
--
-- **主キーは申告番号。** 採番するのは税関で、利用者が持ち込む。採番すると、
-- 投影を読み直すたびに同じ内容の行が積み上がる（IT2 で実在した欠陥）。
--
-- held_business_days は留置の営業日数（港の所在国の休日カレンダーで数え、
-- CustomsStatusChangedEvent.heldBusinessDays から写す）。

CREATE TABLE customs_declaration (
    declaration_number     VARCHAR(50)  PRIMARY KEY,
    tracking_number        VARCHAR(25)  NOT NULL,
    booking_id             VARCHAR(36)  NOT NULL,
    status                 VARCHAR(30)  NOT NULL,
    declared_at            TIMESTAMPTZ  NOT NULL,
    last_status_changed_at TIMESTAMPTZ  NOT NULL,
    last_held_at           TIMESTAMPTZ,
    held_business_days     INTEGER      NOT NULL DEFAULT 0,
    last_reason            TEXT,
    changed_by             VARCHAR(50),
    projected_at           TIMESTAMPTZ  NOT NULL
);

-- 貨物から引く（不変条件 3 の「未決着は高々 1 件」の確認と、追跡番号での絞り込み）。
CREATE INDEX idx_customs_declaration_cargo
    ON customs_declaration (tracking_number, status);

-- 一覧は留置営業日の多い順（US29 §受入基準 6 の督促）。
CREATE INDEX idx_customs_declaration_held
    ON customs_declaration (status, held_business_days DESC);
