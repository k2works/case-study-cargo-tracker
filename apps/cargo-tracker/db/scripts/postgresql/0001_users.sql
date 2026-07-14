-- users / user_roles（認証・RBAC・ADR-0005）。PostgreSQL 方言。
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(200) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users(id),
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);
