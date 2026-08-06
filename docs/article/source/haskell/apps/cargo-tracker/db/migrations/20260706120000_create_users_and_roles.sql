-- migrate:up

-- IT1 AUTH 認証ユーザー
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(60) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX users_email_idx ON users (email);

-- 8 ロール (Shipper, Consignee, Sales, Router, Tracker, Handler, Accountant, MasterAdmin)
-- 1 ユーザーが複数ロールを持てる構造 (将来拡張のため)
CREATE TABLE user_roles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_roles_role_check
        CHECK (role IN ('Shipper','Consignee','Sales','Router','Tracker','Handler','Accountant','MasterAdmin')),
    UNIQUE (user_id, role)
);

CREATE INDEX user_roles_user_id_idx ON user_roles (user_id);

-- migrate:down

DROP TABLE user_roles;
DROP TABLE users;
