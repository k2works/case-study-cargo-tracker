-- 通関申告のスキーマ 2 件（IT13 負債枠 2。IT12 レビュー #10・#L15）。
--
-- 正典: docs/design/cargo-tracker/data-model.md「customs_declaration」
--
-- **V006 は編集しない。** 適用済みの版を書き換えると、動いているクラスタだけが
-- checksum mismatch で起動しなくなる（CI はまっさらな DB に当てるので現れない）。
-- 直し方は新しい版を足すことである。

-- (1) held_business_days を落とす。
--
-- **列は常に 0 だった。** 確定値を持つのは契約イベント CustomsStatusChangedEvent
-- だが、投影は内部イベントだけを読むので写す相手がいない。営業日数は留置中も
-- 決着後も**読むときに数える**（CustomsQueryHandler）。休日カレンダーを知っている
-- のはドメインなので、SQL に写すと同じ判定が 2 か所になって片方だけ直る。
--
-- 留置営業日の多い順に並べる索引も一緒に落とす（並べ替えも読むときに行うので、
-- この索引は誰も使わない）。DROP COLUMN は依存する索引を連れて消える。
DROP INDEX IF EXISTS idx_customs_declaration_held;
ALTER TABLE customs_declaration DROP COLUMN held_business_days;

-- 一覧は既定で通関済を外す（status <> 'CLEARED'）。絞りは残るので索引も残す。
CREATE INDEX IF NOT EXISTS idx_customs_declaration_status
    ON customs_declaration (status);

-- (2) 不変条件 3 を DB でも守る。
--
-- **画面の確認だけでは同時の 2 件が通る。** 登録は投影を読んでからコマンドを送るので、
-- 2 つの要求が同時に来ると両方とも「未決着は無い」を見る。未決着（PENDING / HELD）は
-- 貨物ごとに高々 1 件——決着した申告（CLEARED / REJECTED）は数えないので、
-- 不可のあとに出し直せる。
--
-- **既にある行が違反していると、この版は当たらない**（動いているクラスタだけが
-- 起動しなくなる）。当てる前に次で確かめる:
--   SELECT tracking_number FROM customs_declaration
--    WHERE status IN ('PENDING','HELD') GROUP BY tracking_number HAVING count(*) > 1;
CREATE UNIQUE INDEX uq_customs_declaration_unsettled
    ON customs_declaration (tracking_number)
 WHERE status IN ('PENDING', 'HELD');
