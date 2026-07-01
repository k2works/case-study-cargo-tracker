-- migrate:up

-- IT5 task 2.1 (IT4 繰越) US13 予約確定 + キャンセル - cargo カラム拡張
--
-- iteration_plan-4.md §4.3 の DDL を IT5 で migration 化。
-- 予約確定と 3 段階キャンセル料 (Free/Partial/Full, ADR-0007 採用済) を保存。
--
-- BookingStatus 遷移: PRELIMINARY → ROUTE_PROPOSED → ROUTE_ASSIGNED → CONFIRMED → CANCELLED
-- (既存 create_cargo.sql で PRELIMINARY / ROUTE_PROPOSED / ROUTE_ASSIGNED / CONFIRMED / TRACKING_ISSUED / IN_TRANSIT / DELIVERED / SETTLED / CANCELLED の 9 状態は定義済み)

ALTER TABLE cargo
  ADD COLUMN itinerary_id               UUID REFERENCES itinerary(itinerary_id),
  ADD COLUMN cancellation_rate          NUMERIC(4,3)
             CHECK (cancellation_rate IS NULL
                    OR (cancellation_rate >= 0.000 AND cancellation_rate <= 1.000)),
  ADD COLUMN cancellation_tier          VARCHAR(10)
             CHECK (cancellation_tier IS NULL
                    OR cancellation_tier IN ('FREE','PARTIAL','FULL')),
  ADD COLUMN cancellation_calculated_at TIMESTAMPTZ,
  ADD COLUMN confirmed_at               TIMESTAMPTZ,
  ADD COLUMN cancelled_at               TIMESTAMPTZ;

CREATE INDEX idx_cargo_itinerary ON cargo (itinerary_id);

-- migrate:down

DROP INDEX IF EXISTS idx_cargo_itinerary;

ALTER TABLE cargo
  DROP COLUMN IF EXISTS cancelled_at,
  DROP COLUMN IF EXISTS confirmed_at,
  DROP COLUMN IF EXISTS cancellation_calculated_at,
  DROP COLUMN IF EXISTS cancellation_tier,
  DROP COLUMN IF EXISTS cancellation_rate,
  DROP COLUMN IF EXISTS itinerary_id;
