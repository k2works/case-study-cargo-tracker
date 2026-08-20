-- 危険物クラスを国連分類のコード（1〜9）に揃える（IT3 タスク 0.4）。
-- 適用済みの V1〜V3 は編集しない。編集すると checksum が変わり、既にデプロイ済みの
-- 環境が起動できなくなる。

-- 自由入力だった頃の値（'Class 3'・'3類' など）のうち、分類が読み取れるものだけを揃える。
-- 読み取れない値はそのまま残す。ここで NULL にすると、危険物なのに申告が無い行になり、
-- その予約が開けなくなる。残った値はドメイン側で「分類不明」として読み、
-- 次に編集するときに選び直してもらう。
--
-- 正規表現（PostgreSQL の SUBSTRING ... FROM '...'）は使わない。H2 は同じ構文を
-- 「開始位置の指定」と解釈するため、本番だけが緑になる。LIKE は両方が同じ意味で解釈する。
UPDATE cargo
   SET hazardous_class = CASE
        WHEN hazardous_class LIKE '%1%' THEN '1'
        WHEN hazardous_class LIKE '%2%' THEN '2'
        WHEN hazardous_class LIKE '%3%' THEN '3'
        WHEN hazardous_class LIKE '%4%' THEN '4'
        WHEN hazardous_class LIKE '%5%' THEN '5'
        WHEN hazardous_class LIKE '%6%' THEN '6'
        WHEN hazardous_class LIKE '%7%' THEN '7'
        WHEN hazardous_class LIKE '%8%' THEN '8'
        WHEN hazardous_class LIKE '%9%' THEN '9'
        ELSE hazardous_class
       END
 WHERE hazardous_class IS NOT NULL
   AND hazardous_class NOT IN ('1', '2', '3', '4', '5', '6', '7', '8', '9');
