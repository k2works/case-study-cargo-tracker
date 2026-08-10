-- V1 の `booking_id ... UNIQUE`（単一列）を落とす。V36 の続きである。
--
-- **なぜ common/ に置けないか。** V1 は制約に名前を付けずに書いたため、
-- 名前は DBMS が自動で決める。PostgreSQL は `invoice_booking_id_key`、
-- H2 は `CONSTRAINT_74D6` のような通し番号を付ける。
-- **名前を書き写すと、片方の DBMS でだけ落ちる**
-- （ADR-003 が禁じた「方言の漏れ」そのものである）。
--
-- そこで**名前ではなく形で探す**。落とすのは
-- 「invoice の UNIQUE 制約のうち、列が booking_id 1 つだけのもの」であり、
-- V36 が足した `(booking_id, invoice_type)` は 2 列なので対象にならない。
DO $$
DECLARE
    target text;
BEGIN
    SELECT tc.constraint_name INTO target
      FROM information_schema.table_constraints tc
     WHERE tc.table_name = 'invoice'
       AND tc.constraint_type = 'UNIQUE'
       AND (SELECT count(*)
              FROM information_schema.key_column_usage k
             WHERE k.constraint_name = tc.constraint_name
               AND k.table_name = tc.table_name) = 1
       AND EXISTS (SELECT 1
                     FROM information_schema.key_column_usage k
                    WHERE k.constraint_name = tc.constraint_name
                      AND k.table_name = tc.table_name
                      AND k.column_name = 'booking_id');
    IF target IS NOT NULL THEN
        EXECUTE format('ALTER TABLE invoice DROP CONSTRAINT %I', target);
    END IF;
END $$;
