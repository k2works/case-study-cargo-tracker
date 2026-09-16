-- 認証（US26 / US31）。authms は Event Sourcing を使わない（ADR-0001 決定 2）。
--
-- 正典: docs/design/cargo-tracker/data-model.md

CREATE TABLE users (
    username           VARCHAR(50)  PRIMARY KEY,
    password_hash      VARCHAR(100) NOT NULL,
    display_name       VARCHAR(100) NOT NULL,
    -- ROLE_SHIPPER のときだけ荷主 ID を持つ。自社の予約・請求だけを読む絞り込みに使う。
    shipper_id         VARCHAR(36),
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts    INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE TABLE user_roles (
    username VARCHAR(50) NOT NULL REFERENCES users (username) ON DELETE CASCADE,
    role     VARCHAR(30) NOT NULL,
    PRIMARY KEY (username, role)
);

-- 認証の試行を残す。成功も失敗も残さないと、攻撃と操作ミスの区別がつかない。
CREATE TABLE auth_audit_log (
    id          BIGSERIAL   PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    event       VARCHAR(30) NOT NULL,
    succeeded   BOOLEAN     NOT NULL,
    remote_addr VARCHAR(45),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_audit_log_username_time ON auth_audit_log (username, occurred_at DESC);
