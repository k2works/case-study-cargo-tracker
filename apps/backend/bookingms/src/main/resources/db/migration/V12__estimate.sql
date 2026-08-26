-- 輸送見積（US01・IT12）。

-- 見積番号（[ADR-011] の請求番号と同じ形）。**組み立てはここに置く**——
-- アプリ側で文字列を作ると、別の経路が違う形式を発行できてしまう。
CREATE SEQUENCE estimate_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE estimate (
    id                   BIGSERIAL PRIMARY KEY,
    -- **URL に出るのは UUID である**（[ADR-028] 決定 7）。連番だけにすると、
    -- URL を 1 つ増減させて他の荷主の見積が開ける
    estimate_id          VARCHAR(36)   NOT NULL UNIQUE,
    -- 荷主と電話で読み合わせる番号（受入基準 01-4）
    estimate_number      VARCHAR(20)   NOT NULL UNIQUE,
    -- 地点は論理参照（[ADR-010]）。港が統廃合されても見積の記録は残る
    origin_unlocode      VARCHAR(5)    NOT NULL,
    destination_unlocode VARCHAR(5)    NOT NULL,
    arrival_deadline     DATE          NOT NULL,
    cargo_type           VARCHAR(30)   NOT NULL,
    weight_kg            NUMERIC(10,3) NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ルート候補（受入基準 01-3）。**4 項目を持つ**——航海番号・経由港・所要日数・概算料金。
CREATE TABLE route_candidate (
    id             BIGSERIAL PRIMARY KEY,
    estimate_id    BIGINT      NOT NULL REFERENCES estimate (id),
    voyage_number  VARCHAR(20) NOT NULL,
    -- 直行なら経由港を持たない
    transit_port   VARCHAR(5),
    transit_days   INTEGER     NOT NULL,
    estimated_cost NUMERIC(12,2) NOT NULL,
    -- 推奨順。**順序に意味がある**——上から見せる
    rank           INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_route_candidate_rank UNIQUE (estimate_id, rank)
);

CREATE INDEX idx_route_candidate_estimate ON route_candidate (estimate_id);
