-- 荷主への通知の記録と、追跡番号の採番（US12・US14・ADR-021 / ADR-022）。

-- 通知した「いつ・誰が」で 1 組（US12-4）。
--
-- NOT NULL にしない。列が無かったころの行（IT2〜IT5 で入った予約）が読めなくなる。
-- 通知していない予約では両方 NULL であり、それは「記録が無い」を表す。
ALTER TABLE cargo ADD COLUMN route_notified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE cargo ADD COLUMN route_notified_by VARCHAR(100);

-- 追跡番号の採番（ADR-011 と同じ形）。
--
-- 組み立てはここに置く。アプリ側で文字列を作ると、別の経路（移行・運用スクリプト）が
-- 違う形式を発行できてしまい、サービスをまたいだ照合が壊れる。
-- 形式は TRK-yyyyMMdd-nnnn（17 文字。VARCHAR(20) に収まる）。
CREATE SEQUENCE tracking_number_seq START WITH 1 INCREMENT BY 1;

-- tracking_number の列をここで足す。
--
-- `data-model.md` の cargo には以前から載っていたが、実装（V3）には無かった（設計が先行していた）。
-- 一覧・詳細で読むため、ここで実体を作る。
--
-- 一意にしないと、採番の経路が 2 つできたときに同じ番号の予約が 2 件並ぶ。荷主から
-- 番号で問い合わせを受けても、どちらの貨物か決まらない。
-- 未発行は NULL であり、PostgreSQL も H2 も NULL は重複とみなさない。
ALTER TABLE cargo ADD COLUMN tracking_number VARCHAR(20);
ALTER TABLE cargo ADD CONSTRAINT uk_cargo_tracking_number UNIQUE (tracking_number);
