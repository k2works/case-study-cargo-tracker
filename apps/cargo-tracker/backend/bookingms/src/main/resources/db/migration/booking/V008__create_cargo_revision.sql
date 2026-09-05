-- 予約の修正履歴（US32 §受入基準 4「何を変えたか」）。
--
-- 正典: docs/design/cargo-tracker/data-model.md / ADR-0008
--
-- IT4 では「変更内容の履歴テーブルは作らない。Event Store が持つ」と決めた。その結果、
-- 何を変えたかを読む手段がどこにも無かった（IT4 引き継ぎ 2）。判断を改め、投影として
-- 持つ。行は修正イベントから決まりきった形で導くので、リプレイしても増えない。
--
-- 主キーに updated_at を含める。連番で採ると、リプレイのたびに新しい番号が振られて
-- 同じ修正が積み上がる（追記専用の行はリプレイで増える）。
CREATE TABLE cargo_revision (
    booking_id VARCHAR(36) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    field_label VARCHAR(50) NOT NULL,
    -- 並びは貨物仕様の項目順。ラベルの五十音順に並べると読む順が業務と合わない。
    field_seq INTEGER NOT NULL,
    before_value VARCHAR(200) NOT NULL,
    after_value VARCHAR(200) NOT NULL,
    updated_by VARCHAR(50),
    PRIMARY KEY (booking_id, updated_at, field_label)
);

CREATE INDEX idx_cargo_revision_booking ON cargo_revision (booking_id, updated_at DESC);
