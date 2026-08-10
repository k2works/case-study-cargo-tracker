-- 法人荷主への請求か（IT13 レビュー C6）。
--
-- 法人かどうかを割引率から逆算していた。契約はあるが割引条件がまだ登録されて
-- いない法人は率が 0% であり、逆算すると個人として読み戻る。
-- 0% は「法人でない」ではない。
--
-- 既存行は逆算した値で埋める。それが、これまで動いていた解釈である。
ALTER TABLE invoice ADD COLUMN IF NOT EXISTS corporate BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE invoice SET corporate = TRUE WHERE discount_rate > 0;

COMMENT ON COLUMN invoice.corporate IS '法人荷主への請求か。割引率から逆算しない';
