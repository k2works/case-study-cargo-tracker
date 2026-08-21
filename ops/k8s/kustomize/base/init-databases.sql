-- Database per Service の論理分離を保ちつつ、ローカルでは PostgreSQL 1 台に
-- サービス専用のデータベースを作成して起動コストを抑える。
CREATE DATABASE auth_db;
CREATE DATABASE booking_db;
CREATE DATABASE routing_db;
CREATE DATABASE tracking_db;
CREATE DATABASE handling_db;
CREATE DATABASE billing_db;
