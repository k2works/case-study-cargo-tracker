-- E2E テスト用データベースを作成する。
-- 開発用（cargo_tracker）と分けることで、E2E がテストデータを投入・削除しても
-- 日常開発中のデータを壊さない。
CREATE DATABASE cargo_tracker_e2e OWNER cargo_tracker;
