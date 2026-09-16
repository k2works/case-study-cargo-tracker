-- 請求書の発行・入金（US23・IT14）。IT13 が意図して送った負債の回収（注 N2）。
--
-- **列も索引も IT13 では作らなかった。** 書き手が US23 まで居ないので、
-- いま作ると誰も書かない列が残る（IT12 の held_business_days と同じ形）。
-- 書き手ができた本 IT でまとめて足す。
ALTER TABLE invoice ADD COLUMN issued_on DATE;
ALTER TABLE invoice ADD COLUMN due_on DATE;
ALTER TABLE invoice ADD COLUMN paid_at TIMESTAMPTZ;

-- 見積時の概算（注 N12）。**IT13 では常に null だった**——見積を経ない予約
-- しかなかったため。本 IT で入力経路ができる。**見積を経ない予約は今後も
-- null** なので、S61 は両方の場合を出し分ける。
ALTER TABLE invoice ADD COLUMN quoted_amount NUMERIC(14,2);

-- 未払いの一覧（US23 §受入基準 5）。**期限超過は列に持たない**（不変条件 4）
-- ので、状態と期限で絞る。列に持つと、日付が変わるたびに全件を書き換える
-- ことになり、書き換えそこねた行が静かに未払いから漏れる。
CREATE INDEX idx_invoice_status_due ON invoice (billing_status, due_on);
