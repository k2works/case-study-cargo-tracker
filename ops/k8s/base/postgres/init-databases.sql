-- Database per Service（ADR-0001 決定 1）。
-- サービスごとに DB と接続ユーザーを分け、他サービスの DB には権限を与えない。
--
-- 正典: docs/design/cargo-tracker/data-model.md「サービスごとの DB」

CREATE USER authms     WITH PASSWORD 'authms';
CREATE USER bookingms  WITH PASSWORD 'bookingms';
CREATE USER routingms  WITH PASSWORD 'routingms';
CREATE USER trackingms WITH PASSWORD 'trackingms';
CREATE USER handlingms WITH PASSWORD 'handlingms';
CREATE USER billingms  WITH PASSWORD 'billingms';
-- 業務ではなく「業務が成立していることを確かめる手段」（[ADR-0020]）。
-- 実行の記録だけを持ち、業務データは各サービスが本番の経路で作る。
CREATE USER simulationms WITH PASSWORD 'simulationms';

CREATE DATABASE auth_db          OWNER authms;
CREATE DATABASE booking_read_db  OWNER bookingms;
CREATE DATABASE routing_read_db  OWNER routingms;
CREATE DATABASE tracking_read_db OWNER trackingms;
CREATE DATABASE handling_read_db OWNER handlingms;
CREATE DATABASE billing_read_db  OWNER billingms;
CREATE DATABASE simulation_read_db OWNER simulationms;
