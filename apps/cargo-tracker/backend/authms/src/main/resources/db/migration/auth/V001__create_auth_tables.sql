-- authms は Event Sourcing を使わない（ADR-0001 決定 2）ので token_entry を持たない。
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- 実テーブル（users / user_roles / user_shipper_link / auth_audit_log）は
-- US26 のタスク 4.2 で追加する。ここでは Flyway の配線だけを通す。

CREATE TABLE schema_bootstrap (
    id INTEGER PRIMARY KEY
);
