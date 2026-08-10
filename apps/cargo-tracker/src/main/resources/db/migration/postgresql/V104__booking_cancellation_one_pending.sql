-- 決着していない申請は 1 予約 1 件まで（US30）。
--
-- **ADR-018 と同型の不変条件を口約束にしない。** 2 件並ぶと、追跡管理者は
-- 同じ貨物について 2 度承認でき、**陸揚げ地が 2 か所決まる**。
--
-- 部分ユニーク索引は H2 が解釈できないため common/ に置けない（ADR-003）。
-- **ローカル（H2）ではこの制約が働かない**ため、ドメイン
-- （CancellationRequest / 申請のコマンドサービス）でも同じことを守る。
CREATE UNIQUE INDEX uq_booking_cancellation_pending
    ON booking_cancellation (booking_id)
    WHERE status = 'PENDING';
