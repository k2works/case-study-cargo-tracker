-- 追跡イベントの出どころと記録者（US17 / IT8）。
--
-- 手動更新（出港・入港など荷役を伴わない状態変化）を同じ表に入れるため、
-- **荷役由来と手で入れたものを区別できるようにする。**
-- 混ぜたままにすると「誰がいつ手で入れたか」を後から追えない。
ALTER TABLE tracking_handling_event
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'HANDLING';

-- 記録者。手動更新でのみ入る（荷役由来の担当者は handling_activity 側が持つ）。
ALTER TABLE tracking_handling_event
    ADD COLUMN recorded_by VARCHAR(50);

-- **既定を HANDLING にしている理由**: 既存行はすべて荷役由来である。
-- 後から区別できなくなることはない。
ALTER TABLE tracking_handling_event
    ADD CONSTRAINT chk_tracking_event_source CHECK (source IN ('HANDLING', 'MANUAL'));

-- 追跡イベントの種別を DB でも固定する。
--
-- **これまで tracking_handling_event に種別の制約が無かった。** 荷役側
-- （handling_activity）にはあるが、追跡側は何でも入る状態だった。
-- 手動更新で種別が 3 つ増えるこの機会に、書ける値を宣言しておく。
--
-- **DEPART（出港）と ARRIVE（入港）は荷役作業ではない。** 船が出入りしたことは
-- 荷役作業員の記録に現れず、手で入れる以外に追跡へ反映する手段が無い。
-- AWAIT_CLAIM（引取待ち）は、これまでどの経路からも設定できない状態だった。
ALTER TABLE tracking_handling_event
    ADD CONSTRAINT chk_tracking_event_type
    CHECK (event_type IN ('RECEIVE', 'LOAD', 'UNLOAD', 'CUSTOMS', 'CLAIM',
                          'DEPART', 'ARRIVE', 'AWAIT_CLAIM'));
