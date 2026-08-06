-- migrate:up

-- UN/LOCODE マスタ (共有カーネル)
-- IT1 では最小限のシード (主要 10 港) を含む
CREATE TABLE location (
    unlocode    VARCHAR(5) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    country     VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT location_unlocode_format
        CHECK (unlocode ~ '^[A-Z]{2}[A-Z0-9]{3}$')
);

-- 最小シード (IT1 デモ用 10 港)
INSERT INTO location (unlocode, name, country) VALUES
    ('JPTYO', 'Tokyo',         'Japan'),
    ('JPOSA', 'Osaka',         'Japan'),
    ('JPYOK', 'Yokohama',      'Japan'),
    ('USNYC', 'New York',      'United States'),
    ('USLAX', 'Los Angeles',   'United States'),
    ('USSEA', 'Seattle',       'United States'),
    ('CNSHA', 'Shanghai',      'China'),
    ('HKHKG', 'Hong Kong',     'Hong Kong'),
    ('SGSIN', 'Singapore',     'Singapore'),
    ('GBLON', 'London',        'United Kingdom');

-- migrate:down

DROP TABLE location;
