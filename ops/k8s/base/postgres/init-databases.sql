-- Database per Service（ADR-0001 決定 1）。
-- サービスごとに DB と接続ユーザーを分け、他サービスの DB には権限を与えない。
--
-- 正典: docs/design/cargo-tracker/data-model.md「サービスごとの DB」
--
-- **何度流しても同じ結果になるように書く。** この SQL は「DB が空のときの
-- 初期化」としてしか走らない（compose も Kustomize も、初回だけ実行する）。
-- サービスを足したとき、**既に動いている環境には反映されない**——実際に
-- simulationms を足したとき、クラスタだけが認証失敗で起動しなかった（IT16）。
-- 足りない分だけを流し直せるように、既にあるものは黙って飛ばす。

DO $$
BEGIN
    CREATE USER authms WITH PASSWORD 'authms';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE USER bookingms WITH PASSWORD 'bookingms';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE USER routingms WITH PASSWORD 'routingms';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE USER trackingms WITH PASSWORD 'trackingms';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE USER handlingms WITH PASSWORD 'handlingms';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE USER billingms WITH PASSWORD 'billingms';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 業務ではなく「業務が成立していることを確かめる手段」（[ADR-0020]）。
-- 実行の記録だけを持ち、業務データは各サービスが本番の経路で作る。
DO $$
BEGIN
    CREATE USER simulationms WITH PASSWORD 'simulationms';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- **CREATE DATABASE は DO ブロックに置けない**（トランザクション内で実行できない）。
-- \gexec で「無いときだけ」流す。
SELECT 'CREATE DATABASE auth_db OWNER authms'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
SELECT 'CREATE DATABASE booking_read_db OWNER bookingms'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'booking_read_db')\gexec
SELECT 'CREATE DATABASE routing_read_db OWNER routingms'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'routing_read_db')\gexec
SELECT 'CREATE DATABASE tracking_read_db OWNER trackingms'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tracking_read_db')\gexec
SELECT 'CREATE DATABASE handling_read_db OWNER handlingms'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'handling_read_db')\gexec
SELECT 'CREATE DATABASE billing_read_db OWNER billingms'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'billing_read_db')\gexec
SELECT 'CREATE DATABASE simulation_read_db OWNER simulationms'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'simulation_read_db')\gexec
