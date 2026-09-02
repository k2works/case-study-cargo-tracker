-- 誤配が起きた事実（US28-2・US28-8・[ADR-026] 決定 3）。
--
-- **状態ではなく、起きたことである。** 経路の状況（routing_status）は再設計で ROUTED へ
-- 戻るが、この 2 列は戻らない——**料金調整の根拠**として参照されるため、解決や再設計で
-- 消してはいけない。
--
-- **例外の履歴（trackingms）に置かない。** 解決時の扱い次第で消えるうえ、料金調整の主体
-- （bookingms → billingms）から 2 ホップ遠い。
ALTER TABLE cargo ADD COLUMN misrouted_at TIMESTAMP WITH TIME ZONE;

-- どこで経路から外れたか。「誤配があった」だけでは、荷主にも経理にも説明できない
ALTER TABLE cargo ADD COLUMN misrouted_location_unlocode VARCHAR(5);
