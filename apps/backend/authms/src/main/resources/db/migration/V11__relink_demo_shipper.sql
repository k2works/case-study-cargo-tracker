-- 動作確認用の荷主利用者を、**実業務の帯の荷主**へ紐付け直す。
--
-- V7 は `shipper_id = 1` へ決め打ちで紐付けていたが、bookingms は荷主の種を
-- 持たなかったため、**1 番が誰になるかはその環境で最初に登録された荷主**で決まる。
-- DB を初期化した直後の 1 番は、シミュレーションが作った荷主だった
-- ——shipper01 で「自分の貨物」を開くと架空の案件が並ぶ。
--
-- bookingms の V13 が 9001 番を動作確認用として予約した。そこへ付け直す。
--
-- **番号で紐付けること自体は残る。** サービスが分かれている以上、authms は
-- bookingms の採番を知らない。番号を**予約する**ことで、環境が変わっても
-- 同じ相手を指すようにしている。
UPDATE user_shipper_link
   SET shipper_id = 9001
 WHERE user_id = (SELECT id FROM users WHERE username = 'shipper01');
