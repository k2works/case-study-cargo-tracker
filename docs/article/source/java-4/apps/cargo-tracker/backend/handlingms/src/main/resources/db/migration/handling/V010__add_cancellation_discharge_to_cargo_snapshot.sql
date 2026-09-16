-- 承認したキャンセルの陸揚げ地（US30・IT15）。
--
-- **キャンセルされた貨物は作業一覧から外れる**が、指定した港の荷降しだけは
-- 残る——降ろさなければ貨物は船の上に残り、追跡も閉じない。どの港で降ろすかを
-- 写しが持たないと、現場はその作業を見つけられない。
--
-- 列が無かったころの行はすべて NULL（キャンセルされていない貨物）で、既定値が
-- 業務上正しい。
ALTER TABLE cargo_snapshot
    ADD COLUMN cancellation_discharge_unlocode VARCHAR(5);

COMMENT ON COLUMN cargo_snapshot.cancellation_discharge_unlocode IS
    '承認されたキャンセルの陸揚げ地。NULL はキャンセルされていない貨物';
