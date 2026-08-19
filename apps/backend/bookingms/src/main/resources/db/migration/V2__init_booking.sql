-- bookingms の実スキーマ（data-model.md の booking_db）。
-- IT1 では shipper のみを追加する。location / cargo / leg は IT2 で追加する。
-- 既にデプロイ済みの環境があるため V1 は変更しない。

CREATE TABLE shipper (
    id              BIGSERIAL PRIMARY KEY,
    shipper_code    VARCHAR(20)  NOT NULL UNIQUE,
    shipper_type    VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(200) NOT NULL,
    address         VARCHAR(500) NOT NULL,
    phone           VARCHAR(50),
    -- 法人固有の契約条件は US03（IT2）で扱う。列だけ先に用意する
    contract_number VARCHAR(50),
    discount_rate   NUMERIC(5,4),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 荷主コードは本番経路（このシーケンス）で採番する。アプリ側で MAX+1 のように
-- 自前採番すると、原因でない他の登録が UNIQUE 制約で落ちる
CREATE SEQUENCE shipper_code_seq START WITH 1 INCREMENT BY 1;

-- メールアドレスは重複を許す（同姓同名・同一メールの別部署がありうるため UNIQUE にしない）。
-- 重複の検出は登録時に問いかける形で行う
CREATE INDEX idx_shipper_email ON shipper (email);
