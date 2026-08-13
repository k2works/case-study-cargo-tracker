-- 引取確認と荷受人（IT7 / US16）。
--
-- ## handling_activity への引取確認
--
-- 引き渡し証明は事故時の唯一の防御線である（ui_design.md）。「渡した」
-- 「受け取っていない」の争いになったとき、確認の記録が無ければ会社が負う。
-- それでいて IT6 の時点では、記録する列が存在しなかった。
--
-- CHECK 制約を置くのは、**画面のバリデーションだけに頼らないため**である。
-- 画面を経由しない登録経路（将来の API・データ移行）でも守られる必要がある。
-- shipper の法人契約（V1 の chk_shipper_corporate_contract）と同じ形である。
--
-- ## cargo への荷受人住所
--
-- data-model.md が「V1 に無い。追加が必要」と明記していた列である。
-- 氏名とメールは V1 にあり、住所だけが欠けていた。
--
-- **3 項目とも NULL 許容のままとする。** 荷受人は予約の時点では未確定でありうる
-- （国際輸送では荷受人が後から決まることがある）。必須にすると、
-- 荷受人が決まるまで予約を登録できなくなる。

ALTER TABLE handling_activity
    ADD COLUMN claim_confirmation_method VARCHAR(30);

ALTER TABLE handling_activity
    ADD COLUMN claim_confirmation_code VARCHAR(50);

ALTER TABLE handling_activity
    ADD COLUMN claim_consignee_name VARCHAR(200);

-- 引取のときだけ確認が必須である。**引取以外に確認が付いている状態も許さない**
-- （受領に「荷受人確認」が付いている記録は、どちらかの入力誤りである）。
ALTER TABLE handling_activity
    ADD CONSTRAINT chk_handling_claim_confirmation
        CHECK (
            (event_type =  'CLAIM' AND claim_confirmation_method IS NOT NULL
                                   AND claim_confirmation_code   IS NOT NULL
                                   AND claim_consignee_name      IS NOT NULL)
         OR (event_type <> 'CLAIM' AND claim_confirmation_method IS NULL
                                   AND claim_confirmation_code   IS NULL
                                   AND claim_consignee_name      IS NULL)
        );

ALTER TABLE cargo
    ADD COLUMN consignee_address VARCHAR(500);
