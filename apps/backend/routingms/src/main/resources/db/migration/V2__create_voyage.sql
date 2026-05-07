-- Routing Microservice: location, voyage, carrier_movement tables

CREATE TABLE location (
    id           BIGSERIAL PRIMARY KEY,
    unlocode     VARCHAR(5)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    country_code VARCHAR(2),
    time_zone    VARCHAR(50),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE voyage (
    id             BIGSERIAL PRIMARY KEY,
    voyage_number  VARCHAR(20) NOT NULL UNIQUE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE carrier_movement (
    id                            BIGSERIAL PRIMARY KEY,
    voyage_id                     BIGINT NOT NULL REFERENCES voyage(id),
    departure_location_unlocode   VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    arrival_location_unlocode     VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    departure_date                TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_date                  TIMESTAMP WITH TIME ZONE NOT NULL,
    seq_number                    INTEGER NOT NULL,
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Seed location data (UN/LOCODE)
INSERT INTO location (unlocode, name, country_code, time_zone) VALUES
    ('JPTYO', 'Tokyo',       'JP', 'Asia/Tokyo'),
    ('JPOSA', 'Osaka',       'JP', 'Asia/Tokyo'),
    ('JPNGO', 'Nagoya',      'JP', 'Asia/Tokyo'),
    ('CNSHA', 'Shanghai',    'CN', 'Asia/Shanghai'),
    ('CNHKG', 'Hong Kong',   'CN', 'Asia/Hong_Kong'),
    ('KRPUS', 'Busan',       'KR', 'Asia/Seoul'),
    ('SGSIN', 'Singapore',   'SG', 'Asia/Singapore'),
    ('USNYC', 'New York',    'US', 'America/New_York'),
    ('USLAX', 'Los Angeles', 'US', 'America/Los_Angeles'),
    ('NLRTM', 'Rotterdam',   'NL', 'Europe/Amsterdam'),
    ('DEHAM', 'Hamburg',     'DE', 'Europe/Berlin'),
    ('GBFXT', 'Felixstowe',  'GB', 'Europe/London'),
    ('AUMEL', 'Melbourne',   'AU', 'Australia/Melbourne');
