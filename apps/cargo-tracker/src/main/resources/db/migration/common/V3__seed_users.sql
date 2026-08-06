-- 開発・テスト用の初期利用者。
--
-- パスワードはすべて "password"（BCrypt コスト 12）。
-- **本番環境ではこのマイグレーションを適用しない。**
-- Release 1 以降で環境別のシード方針を定める（現時点では開発の足場である）。
INSERT INTO users (username, email, password, enabled) VALUES
  ('sales',    'sales@example.com',    '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('router',   'router@example.com',   '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('tracker',  'tracker@example.com',  '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('handler',  'handler@example.com',  '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
  ('billing',  'billing@example.com',  '$2a$12$v/K6CHRkG4CbgFCgknn9qeuUIVlDAjo2qjnsOAw4pTxXAwqpscFZe', TRUE),
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
SELECT id, 'ROLE_SALES'   FROM users WHERE username = 'disabled';
