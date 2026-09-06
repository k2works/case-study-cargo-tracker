-- 経路探索の条件（US10）。
--
-- 正典: docs/design/cargo-tracker/data-model.md
--
-- 条件は投影から組む（RouteSearchRequest の javadoc）。画面から組み立てて送ると、
-- 期限を直したのに古い期限で探すことが起きる。V010 で期限は書き換わるようにしたが、
-- **除外港と探索の起点は投影に置き場が無く、調整しても捨てられていた**。
--
-- 除外港はカンマ区切りで持つ。1 予約あたり数件で、絞り込みにも並び替えにも使わず、
-- 予約と一緒にしか読まない。表を分けると、経路候補を出すたびに JOIN が増える。
ALTER TABLE cargo_summary ADD COLUMN route_exclude_unlocodes VARCHAR(500);
ALTER TABLE cargo_summary ADD COLUMN route_depart_from_unlocode VARCHAR(5);
