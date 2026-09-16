-- 調整に識別子を持たせ、取り消せるようにする（IT14 引き継ぎ C）。
--
-- 誤入力は起きる。符号を取り違えた調整が入ったまま請求書を発行すると、荷主に
-- 誤った額を請求することになる。発行（US23）を足す前に戻せる道を作る。
--
-- **消さずに反対向きを積む。** 取り消しも調整の 1 本として明細に残す——
-- 何が起きたかを追えない記録は、経理にとって根拠にならない。
ALTER TABLE invoice_line_item ADD COLUMN adjustment_id VARCHAR(36);

-- どの調整の取り消しか。入っていれば、その行は「取り消し」である。
ALTER TABLE invoice_line_item ADD COLUMN reversed_adjustment_id VARCHAR(36);

-- 同じ調整を 2 度積まない。**source_event_id の一意とは別の網**である——
-- 集約が同じ識別子の調整を断るのはコマンドの経路だけで、投影は
-- 少なくとも 1 回配送で同じイベントを 2 度受け取りうる。
CREATE UNIQUE INDEX uq_invoice_line_item_adjustment
    ON invoice_line_item (invoice_id, adjustment_id)
 WHERE adjustment_id IS NOT NULL;

-- 取り消しは調整 1 本につき 1 回。二度取り消すと、入れ直したのと同じ額になる。
CREATE UNIQUE INDEX uq_invoice_line_item_reversal
    ON invoice_line_item (invoice_id, reversed_adjustment_id)
 WHERE reversed_adjustment_id IS NOT NULL;
