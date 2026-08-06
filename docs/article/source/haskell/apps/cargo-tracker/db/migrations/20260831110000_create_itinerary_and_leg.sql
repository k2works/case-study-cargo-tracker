-- migrate:up

-- IT5 task 2.1 (IT4 繰越) US09 経路確定 - Itinerary + Leg 永続化
--
-- iteration_plan-4.md §4.3 の DDL を IT5 で migration 化。
-- Domain / Application は IT4 で完成済 (Itinerary.hs / Leg.hs / ItineraryPorts.hs)、
-- Postgres リポジトリと migration のみ IT5 に繰越。
--
-- 集約構造:
--   Booking(Cargo) 1 -- 0..1 Itinerary 1 -- 1..* Leg (seq_number 昇順)
--
-- 制約:
--   - leg (itinerary_id, seq_number) UNIQUE で連番の一意性保証
--   - leg.load_time < leg.unload_time (単一区間の時刻順序)
--   - 隣接 Leg の接続性 (leg[i].unload == leg[i+1].load) は Domain 層で検証

CREATE TABLE itinerary (
    id             BIGSERIAL PRIMARY KEY,
    itinerary_id   UUID NOT NULL UNIQUE,
    booking_id     VARCHAR(20) NOT NULL REFERENCES cargo(booking_id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_itinerary_booking ON itinerary (booking_id);

CREATE TABLE leg (
    id                       BIGSERIAL PRIMARY KEY,
    itinerary_id             UUID NOT NULL
                             REFERENCES itinerary(itinerary_id) ON DELETE CASCADE,
    seq_number               INTEGER NOT NULL CHECK (seq_number >= 1),
    load_location_unlocode   VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    unload_location_unlocode VARCHAR(5) NOT NULL REFERENCES location(unlocode),
    load_time                TIMESTAMPTZ NOT NULL,
    unload_time              TIMESTAMPTZ NOT NULL,
    voyage_number            VARCHAR(20) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (itinerary_id, seq_number),
    CHECK (load_time < unload_time)
);

CREATE INDEX idx_leg_voyage ON leg (voyage_number);

-- migrate:down

DROP TABLE leg;
DROP TABLE itinerary;
