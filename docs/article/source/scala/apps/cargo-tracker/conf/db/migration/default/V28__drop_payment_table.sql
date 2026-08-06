-- IT8 US23 タスク 2.11 (ADR 0019 案 B): payment テーブル drop
-- ADR 0019 で Payment は Invoice 集約内のステータスとして表現する方針 (案 B) を採択したため、
-- V17 で先行作成された payment テーブルは未使用となる。
-- paid_at / payment_reference は V23 で invoice テーブルに統合済。
--
-- 計画上は V25 だが V26 (LossEscalated rename) / V27 (notification CHECK 拡張) 先行作成済のため V28 として実装。
DROP TABLE IF EXISTS payment CASCADE;
