-- 荷主の契約スナップショット（data-model.md「billing_read_db」）。
--
-- billingms が bookingms へ同期問い合わせをしないための ACL の読み取りモデル。
-- 請求書を作るとき（US21・US22）はここから割引率を読み、invoice へ複写する。
-- 作成後に割引率が変わっても、発行済みの請求書は変わらない。
--
-- shipper_name は NULL 許容。crypto-shredding で鍵を破棄すると、リプレイ時に
-- 復号できず NULL が届く（ADR-0003）。NOT NULL にすると投影がそこで止まる。
CREATE TABLE shipper_contract_snapshot (
    shipper_id      VARCHAR(36)  PRIMARY KEY,
    shipper_name    VARCHAR(200),
    shipper_type    VARCHAR(30)  NOT NULL,
    discount_rate   NUMERIC(5,4),
    contract_number VARCHAR(50),
    projected_at    TIMESTAMPTZ  NOT NULL,
    last_event_id   VARCHAR(36)
);
