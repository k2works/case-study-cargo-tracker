-- 請求書に宛名と追跡番号を凍結し、支払いの軸の不変条件を DB でも守る（US23 / IT13 レビュー）。

-- **宛名を凍結する**（IT13 レビュー C7）。
-- 確定済みの請求書の荷主名を毎回 Booking から引き直していたため、
-- **荷主が改名すると発行済みの請求書の宛名が後から変わっていた**。
-- 金額を丸め後のスナップショットで持っているのと同じ理由で、
-- 「誰にいくら請求したか」は請求書自身が持つ。
--
-- **これは N+1 の解決でもある**（C4）。表示に要る値を請求書が持てば、
-- 一覧を描くのに 1 行ずつ ACL ポートを呼ぶ必要が無くなる。
ALTER TABLE invoice ADD COLUMN shipper_name    VARCHAR(200);
ALTER TABLE invoice ADD COLUMN tracking_number VARCHAR(30);

-- **支払いの軸が動くのは料金が確定した後だけ**（ADR-017）。
-- ADR は「US23 の実装者への前提」として書いていたが、DB 制約でも検査でもなく
-- **口約束のままだった**（IT13 レビュー）。軸を分けた判断の慎重さに対して、
-- 分けた後の不変条件が守られていないのは非対称である。
ALTER TABLE invoice ADD CONSTRAINT chk_invoice_payment_after_charge
    CHECK (payment_status = 'PENDING' OR charge_status = 'CONFIRMED');
