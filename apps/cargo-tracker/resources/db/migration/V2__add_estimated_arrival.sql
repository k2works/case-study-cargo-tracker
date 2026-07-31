-- 推定到着日の追加（US18 の受入基準）
-- NULL 許容とするのは、経路が未確定の貨物では到着予定が定まらないためである
-- （docs/design/domain-model.md の段階的導入方針）
-- カラム定義は docs/design/data-model.md を正典とする

ALTER TABLE tracking_activity ADD COLUMN estimated_arrival TIMESTAMP;
