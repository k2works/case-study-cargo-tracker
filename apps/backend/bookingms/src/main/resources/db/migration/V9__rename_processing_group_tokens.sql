-- ADR-0016 / IT8 T1.2：@ProcessingGroup 改名に伴う tokenentry のキー移行（bookingms）
-- 旧 processorname → 新 processorname に UPDATE する。
-- 既処理位置を保持することで Kafka offset 初期化による二重投影リスクを回避する。
-- 注意: テーブル名 tokenentry（小文字・アンダースコアなし）、カラム名 processorname。
--      Axon Framework 5 のデフォルトテーブル定義（V1__create_axon_tables.sql 参照）。

UPDATE tokenentry SET processorname = 'cross-route-confirmed-events'
WHERE processorname = 'route-confirmed';

UPDATE tokenentry SET processorname = 'cross-booking-saga'
WHERE processorname = 'booking-saga';

-- 注: cross-booking-billing は IT7 で既に新規約準拠（改名不要）。
