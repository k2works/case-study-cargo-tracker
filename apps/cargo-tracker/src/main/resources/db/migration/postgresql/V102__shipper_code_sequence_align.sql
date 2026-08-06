-- 荷主コードのシーケンスを既存データの次の値に合わせる（V4 の続き）。
--
-- V4 より前に登録された荷主がある環境（開発・ステージング・本番）では、
-- シーケンスが 1 から始まると既存の荷主コードと衝突する。
--
-- **setval は PostgreSQL 固有であり H2 には存在しない。** common/ に置くと
-- ローカル起動が Flyway のマイグレーション失敗で止まる。H2 はローカル起動専用で
-- 常に空のデータベースであるため、合わせる対象が無く不要である（ADR-003）。

SELECT setval('shipper_code_seq',
              COALESCE((SELECT MAX(CAST(SUBSTRING(shipper_code, 5) AS INTEGER)) FROM shipper), 0) + 1,
              false);
