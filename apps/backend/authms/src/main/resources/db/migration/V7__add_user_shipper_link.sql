-- 荷主利用者と bookingms の荷主 ID を明示的に紐付ける（US33）。
--
-- username・email・shipper_code の文字列一致で推測しない。authms は利用者を持ち、
-- bookingms は荷主を持つため、ここには bookingms 側の業務 ID だけを保持する。
CREATE TABLE user_shipper_link (
    user_id    BIGINT NOT NULL REFERENCES users (id),
    shipper_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_shipper_link PRIMARY KEY (user_id),
    CONSTRAINT uq_user_shipper_link_shipper UNIQUE (shipper_id)
);

CREATE INDEX idx_user_shipper_link_shipper ON user_shipper_link (shipper_id);

-- 開発・検証用の荷主利用者。bookingms の初期荷主 1 件目に対応する。
INSERT INTO user_shipper_link (user_id, shipper_id)
SELECT id, 1 FROM users WHERE username = 'shipper01';
