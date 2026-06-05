-- ShedLock テーブル（IT8 T2.1 / ADR-0017）
-- billingms multi-instance デプロイ時の @Scheduled 排他制御に利用する。
-- net.javacrumbs.shedlock:shedlock-provider-jdbc-template 6.x 仕様準拠。
-- H2 / PostgreSQL 両対応のため TIMESTAMP（TIMESTAMPTZ ではなく）を使う（既存マイグレーションと同様）。

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
