-- IT4 タスク 2.3 / US11: 経路情報を予約に紐付けるカラムを cargo テーブルに追加。
-- itinerary_voyages はカンマ区切り航海番号の順序リスト（NULL = 未紐付け）。
-- 既存行のデータマイグレーションは不要（NULL のままで RouteAssigned 遷移時にのみ書き込まれる）。

ALTER TABLE cargo
    ADD COLUMN itinerary_voyages VARCHAR(200);
