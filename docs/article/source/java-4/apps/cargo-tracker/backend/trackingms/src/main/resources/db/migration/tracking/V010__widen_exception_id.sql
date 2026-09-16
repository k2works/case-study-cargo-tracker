-- 例外の識別子を広げる（IT11 / US28）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「tracking_exception」
--
-- **VARCHAR(36) は「識別子は UUID である」という前提だった。** 誤配の自動起票で
-- 荷役から識別子を導くようになり、前置きを足した形（`MIS-<uuid>` = 40 文字）が
-- 入らずに投影が止まった。導き方は UUID に収める形へ直したが、**列そのものも
-- 前提に寄りかからない幅にしておく**。
--
-- **止まった投影は 1 件の不正な行では済まない。** 書けないイベントが 1 つあると
-- Event Processor はそこで止まり、**後続のイベントが全部届かなくなる**——画面には
-- 「反映されない」としか出ないので、原因に辿り着くまでが長い（IT11 の T6e で実測）。
--
-- 既存の行は影響を受けない（広げるだけ）。

ALTER TABLE tracking_exception
    ALTER COLUMN exception_id TYPE VARCHAR(64);
