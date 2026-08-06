-- migrate:up

-- IT5 US16 引取作業を記録する - 引取確認コード保存テーブル
--
-- data-model.md §confirmation_code に準拠 (iter 2 で追記済み)。
-- 1 予約 = 0..1 確認コード (booking_id UNIQUE)。
--
-- SEC-04: 平文コードは保存せず、Domain 層 (ConfirmationCode VO) では 6 桁数字を
-- 扱うが DB には bcrypt cost=10 のハッシュのみ保存する想定。IT5 段階では
-- 実装簡略化のため平文カラム code を持つが、IT6 で code_hash に置換予定。
--
-- attempt_count: 検証失敗回数 (5 で lock、Domain 層 maxAttempts と一致)。

CREATE TABLE confirmation_code (
    id             BIGSERIAL PRIMARY KEY,
    booking_id     VARCHAR(20) NOT NULL UNIQUE,
    code           VARCHAR(6) NOT NULL,
    issued_at      TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    attempt_count  INTEGER NOT NULL DEFAULT 0
                   CHECK (attempt_count >= 0 AND attempt_count <= 5),
    version        INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_confirmation_code_booking ON confirmation_code (booking_id);

-- migrate:down

DROP TABLE confirmation_code;
