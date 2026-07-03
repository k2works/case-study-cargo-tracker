-- migrate:up

-- IT7 US19/US20 例外処理 - 遅延・破損・紛失例外の記録テーブル
--
-- iteration_plan-7.md §5.3 / ADR-0014 (計画) に対応。
-- 3 種類の例外詳細 (DelayException / DamageException / LossException) を
-- 単一テーブル + JSONB detail_json に統合 (垂直分割回避、検索性維持)。
--
-- Domain: Cargotracker.Exception.Domain.Model.ExceptionRecord
-- Port: Cargotracker.Exception.Application.Ports.ExceptionRepository
-- Commands: Record{Delay,Damage,Loss}ExceptionCommand + ResolveExceptionCommand

CREATE TABLE exception_record (
    id               BIGSERIAL PRIMARY KEY,
    exception_id     VARCHAR(64) NOT NULL UNIQUE,
    tracking_number  VARCHAR(20) NOT NULL,
    exception_type   VARCHAR(20) NOT NULL
                     CHECK (exception_type IN ('DELAY','DAMAGE','LOSS')),
    severity         VARCHAR(10) NOT NULL
                     CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    detail_json      JSONB NOT NULL,
    reporter_user_id VARCHAR(64) NOT NULL,
    reporter_role    VARCHAR(20) NOT NULL,
    reported_at      TIMESTAMPTZ NOT NULL,
    resolved_at      TIMESTAMPTZ,
    version          INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT er_tracking_number_format
        CHECK (tracking_number ~ '^[A-Z0-9]{8}$'),
    CONSTRAINT er_reporter_user_id_nonempty
        CHECK (length(trim(reporter_user_id)) > 0),
    CONSTRAINT er_reporter_role_nonempty
        CHECK (length(trim(reporter_role)) > 0)
);

CREATE INDEX idx_er_by_tracking_number
    ON exception_record (tracking_number, reported_at DESC);
CREATE INDEX idx_er_by_type_severity
    ON exception_record (exception_type, severity);
CREATE INDEX idx_er_unresolved
    ON exception_record (resolved_at)
    WHERE resolved_at IS NULL;

-- migrate:down

DROP TABLE exception_record;
