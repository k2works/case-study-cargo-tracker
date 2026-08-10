-- V1 の `booking_id ... UNIQUE`（単一列）を落とす。V36 の続きである。
--
-- **h2/ に初めてファイルを置く。** application.yml は「原則として空。
-- 増え始めたら共通部分が分岐している兆候」と書いており、その警戒は正しい。
-- ここで分岐する理由は**スキーマの違いではなく、名前の付き方の違い**である。
-- V1 が制約に名前を付けずに書いたため、H2 は `CONSTRAINT_74D6` のような
-- 通し番号を、PostgreSQL は `invoice_booking_id_key` を付ける。
-- 名前を書き写せば片方でだけ落ちる。
--
-- **名前ではなく形で探す。** 落とすのは「invoice の UNIQUE 制約のうち、
-- 列が booking_id 1 つだけのもの」であり、V36 が足した
-- `(booking_id, invoice_type)` は 2 列なので対象にならない。
--
-- PostgreSQL 側は同じ版番号の postgresql/V103 が同じことを DO ブロックで行う。
EXECUTE IMMEDIATE (
    SELECT 'ALTER TABLE invoice DROP CONSTRAINT "' || tc.constraint_name || '"'
      FROM information_schema.table_constraints tc
     WHERE LOWER(tc.table_name) = 'invoice'
       AND tc.constraint_type = 'UNIQUE'
       AND (SELECT count(*)
              FROM information_schema.key_column_usage k
             WHERE k.constraint_name = tc.constraint_name
               AND LOWER(k.table_name) = 'invoice') = 1
       AND EXISTS (SELECT 1
                     FROM information_schema.key_column_usage k
                    WHERE k.constraint_name = tc.constraint_name
                      AND LOWER(k.table_name) = 'invoice'
                      AND LOWER(k.column_name) = 'booking_id')
);
