-- 航海の船名・運送会社・対応貨物種別を追加する（US24 の受入基準）。
--
-- V1 は voyage テーブルを作成したが、航海番号しか持っていなかった。
-- US24 は「航海番号・船名・運送会社・出発港・到着港・出発日・到着日・対応貨物種別を
-- 入力できる」を求めており、**このままでは受入基準を満たせない**
-- （IT3 計画時の突合で発覚。IT2 の V3 と同じ型の欠落）。
--
-- 出発港・到着港・出発日・到着日は carrier_movement が持つ。航海の端点は
-- 「最初の区間の出発地」「最後の区間の到着地」であり、voyage に重複して持たない。
-- **同じ事実を 2 か所に持つと、区間を足したときに端点だけ古いままになる。**

ALTER TABLE voyage ADD COLUMN vessel_name  VARCHAR(100);
ALTER TABLE voyage ADD COLUMN carrier_name VARCHAR(100);

-- 対応貨物種別。カンマ区切りで保持する。
--
-- **正規化して別テーブルにしない。** 値は 3 種類で固定であり（domain-model.md）、
-- 検索は「この航海はこの種別を運べるか」の包含判定のみである。
-- 別テーブルにすると、一覧のたびに JOIN が 1 つ増えるだけで得るものがない。
ALTER TABLE voyage ADD COLUMN cargo_types VARCHAR(100);

-- 既存行（動作確認用データを含む）への既定値。一般貨物のみ扱う航海として扱う。
UPDATE voyage SET cargo_types = 'GENERAL' WHERE cargo_types IS NULL;

-- ここから先に登録される航海は、必ず値を持つ。
ALTER TABLE voyage ALTER COLUMN cargo_types SET NOT NULL;

-- 空文字は「何も運べない航海」であり業務上あり得ない。
ALTER TABLE voyage ADD CONSTRAINT chk_voyage_cargo_types
    CHECK (cargo_types <> '');
