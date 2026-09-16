-- 通関状態の変更履歴（US29 §受入基準 8 / IT12）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「customs_status_history」
--
-- **当初の正典は「履歴は Event Store から読む」だった。実装できなかった。**
-- @QueryHandler から EventStore.transaction(context).source(...) を回すと、
-- タグを指定しても havingAnyTag() でも 0 件になる（実測）。集約の復元は同じ API で
-- 動くので、クエリの ProcessingContext がこの読み方を支えていない。
-- ADR-0012（CargoSnapshot の元イベント）と同じ形で、正典を直して投影にする。
--
-- **主キーは元イベントの識別子。** 追記の表なので、これが無いと読み直すたびに
-- 積み上がる（IT6 の「追記専用の行はリプレイで増える」）。

CREATE TABLE customs_status_history (
    event_id           VARCHAR(64)  PRIMARY KEY,
    declaration_number VARCHAR(50)  NOT NULL,
    kind               VARCHAR(30)  NOT NULL,
    previous_status    VARCHAR(30),
    status             VARCHAR(30),
    reason             TEXT,
    changed_by         VARCHAR(50),
    changed_at         TIMESTAMPTZ  NOT NULL,
    projected_at       TIMESTAMPTZ  NOT NULL
);

-- 履歴は「その申告の、起きた順」でしか読まない。
CREATE INDEX idx_customs_status_history_declaration
    ON customs_status_history (declaration_number, changed_at);
