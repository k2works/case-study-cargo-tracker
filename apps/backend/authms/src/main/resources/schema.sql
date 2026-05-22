CREATE TABLE IF NOT EXISTS users (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    username              VARCHAR(100) NOT NULL UNIQUE,
    password              VARCHAR(255) NOT NULL,
    role                  VARCHAR(50)  NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_at             TIMESTAMP    NULL
);
