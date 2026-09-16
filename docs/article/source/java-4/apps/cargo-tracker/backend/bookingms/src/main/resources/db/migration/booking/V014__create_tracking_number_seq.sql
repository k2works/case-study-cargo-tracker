-- 追跡番号の採番（US14 / IT7）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「採番は投影側」
--
-- **集約で MAX+1 しない。** 同時に 2 件発行したときに同じ番号が出る。
-- booking_number・shipper_code と同じ形にする（シーケンス）。
--
-- cargo_summary.tracking_number は V004 で UNIQUE 付きで作ってある。

CREATE SEQUENCE tracking_number_seq START WITH 1 INCREMENT BY 1;
