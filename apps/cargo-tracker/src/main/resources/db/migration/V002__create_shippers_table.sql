CREATE TABLE shippers (
    id            UUID         NOT NULL,
    name          VARCHAR(200) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    phone         VARCHAR(20),
    address       TEXT,
    category      VARCHAR(20)  NOT NULL,
    contract_number VARCHAR(50),
    discount_rate NUMERIC(5, 2),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_shippers PRIMARY KEY (id),
    CONSTRAINT uq_shippers_email UNIQUE (email)
);
