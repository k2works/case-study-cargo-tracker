-- 開発・テスト用の初期利用者。
--
-- パスワードはすべて "password"（BCrypt コスト 12）でリポジトリに公開されている。
--
-- **本番環境には適用しない。** かつては db/migration/common に置き、コメントで
-- 「本番では適用しない」と宣言していたが、common は全プロファイルの locations に
-- 含まれるため何も強制していなかった。**宣言ではなく配置で守る。**
-- 本ファイルは db/seed 配下にあり、locations に db/seed を明示した環境
-- （local / dev / test）でのみ適用される。
--
-- バージョンを 800 番台にしているのは、業務スキーマのマイグレーション
-- （V1 から順に増える）と番号が衝突しないようにするためである。
INSERT INTO users (username, email, password, enabled) VALUES
  ('sales',    'sales@example.com',    '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('router',   'router@example.com',   '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('tracker',  'tracker@example.com',  '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('handler',  'handler@example.com',  '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('billing',  'billing@example.com',  '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  -- 荷主・荷受人（US18 / IT7）。**開いてよいと定めた画面を開ける利用者がいなかった。**
  -- Role 列挙子には SHIPPER・CONSIGNEE があり、ui_design.md も貨物追跡を
  -- この 2 ロールに開くと定めていたが、利用者が 1 人も存在しなかった。
  -- IT5 は「実行できる人」、IT6 は「実行する人の入口」、IT7 は「その人自体の不在」である。
  ('shipper',   'shipper@example.com',   '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('consignee', 'consignee@example.com', '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('disabled', 'disabled@example.com', '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', FALSE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SALES'   FROM users WHERE username = 'sales';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ROUTER'  FROM users WHERE username = 'router';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_TRACKER' FROM users WHERE username = 'tracker';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_HANDLER' FROM users WHERE username = 'handler';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_BILLING' FROM users WHERE username = 'billing';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SHIPPER'   FROM users WHERE username = 'shipper';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_CONSIGNEE' FROM users WHERE username = 'consignee';
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SALES'   FROM users WHERE username = 'disabled';
