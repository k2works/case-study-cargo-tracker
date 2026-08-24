-- 通関申告と、その状態変更の履歴（US29・UC21・[ADR-025]）。
--
-- **data-model.md の DDL をそのまま落とす。** IT9 で新しく決めたのではなく、
-- 設計にすでに定義がある。突き合わせは SchemaDesignConsistencyTest が行う。

CREATE TABLE customs_declaration (
    id                 BIGSERIAL PRIMARY KEY,
    -- 税関から受け取る業務キー。**採番するのはこちらではない**
    declaration_number VARCHAR(50) NOT NULL UNIQUE,
    -- booking_db への論理参照（Database per Service）。FK は張らない
    booking_id         VARCHAR(20) NOT NULL,
    -- 荷役作業員は予約番号を知らない。申告の入力キーは追跡番号である（US15-1）
    tracking_number    VARCHAR(20) NOT NULL,
    declared_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 登録の時点で通関済を選べると、引取のガードが最初から素通りになる
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    cleared_at         TIMESTAMP WITH TIME ZONE,
    remarks            VARCHAR(500),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 引取のガードは booking_id で引く。留置の督促は状態と申告日時で絞る
CREATE INDEX idx_customs_declaration_booking ON customs_declaration (booking_id);
CREATE INDEX idx_customs_declaration_status ON customs_declaration (status, declared_at);

-- 状態変更の監査履歴（US29-8）。**追記しかしない**。
--
-- from_status も NOT NULL である。登録そのものも 1 行目として残すため、
-- 初回は PENDING → PENDING になる。空にすると「登録なのか、前の状態が
-- 分からないのか」が読めない。
CREATE TABLE customs_status_history (
    id                     BIGSERIAL PRIMARY KEY,
    customs_declaration_id BIGINT       NOT NULL REFERENCES customs_declaration(id),
    from_status            VARCHAR(30)  NOT NULL,
    to_status              VARCHAR(30)  NOT NULL,
    changed_by             VARCHAR(100) NOT NULL,
    changed_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- **理由は必須**（US29-2）。空で通すと、監査の履歴が「誰かが変えた」だけになる
    reason                 VARCHAR(500) NOT NULL
);

-- 督促の判定は「最新の HELD 遷移日時」から数える（data-model.md の注）
CREATE INDEX idx_customs_status_history_declaration
    ON customs_status_history (customs_declaration_id, changed_at);
