-- 動作確認用の管理者（US33）。
--
-- **ロック解除の機能を作っても、実行できる人がいなければ誰も使えない。**
-- Role.ADMIN は実装にあり non_functional.md の RBAC 表（正典）にも
-- 「システム管理者 / 全画面」とあるが、ログインできる管理者が 1 人も
-- 存在しなかった（IT5 の開始準備で発覚。IT3 の「港マスタが空」と同型）。
--
-- 本ファイルは db/seed 配下にあり、locations に db/seed を明示した環境
-- （local / dev / test）でのみ適用される。**本番には載らない**ことは
-- MigrationLocationsTest が検査する。
INSERT INTO users (username, email, password, enabled) VALUES
  ('admin', 'admin@example.com', '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'admin';
