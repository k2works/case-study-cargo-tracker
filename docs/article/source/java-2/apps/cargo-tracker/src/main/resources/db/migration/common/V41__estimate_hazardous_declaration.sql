-- 見積の危険物申告（US01 の受入基準 6）。
--
-- **入力させたものを捨てない。** 作成フォームに危険物クラス・UN 番号・正式輸送品名の
-- 欄を出しながら保存していなかった。入れた人は保存されたと思うが、詳細にも出ず、
-- 予約にも引き継がれない —— **押しても何も起きない画面**と同じである。
--
-- 予約側（cargo）は US05 で同じ 3 項目を持つ。見積から予約へ引き継ぐことで、
-- **同じ内容を 2 度入力させない**（ui_design.md の画面遷移図）。
ALTER TABLE estimate ADD COLUMN hazard_class VARCHAR(10);
ALTER TABLE estimate ADD COLUMN un_number VARCHAR(10);
ALTER TABLE estimate ADD COLUMN proper_shipping_name VARCHAR(200);
