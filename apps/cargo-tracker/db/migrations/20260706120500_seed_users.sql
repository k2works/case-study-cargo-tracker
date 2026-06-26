-- migrate:up

-- IT1 デモ用シードユーザー
-- 共通パスワード: "password" (bcrypt cost=4)
-- ハッシュ生成: BCrypt.hashPasswordUsingPolicy (HashingPolicy 4 "$2b$") "password"

INSERT INTO users (user_id, email, password_hash) VALUES
  ('admin',      'admin@example.com',      '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG'),
  ('sales',      'sales@example.com',      '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG'),
  ('router',     'router@example.com',     '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG'),
  ('tracker',    'tracker@example.com',    '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG'),
  ('handler',    'handler@example.com',    '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG'),
  ('accountant', 'accountant@example.com', '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG'),
  ('shipper',    'shipper@example.com',    '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG'),
  ('consignee',  'consignee@example.com',  '$2b$04$UxuAUUj0IPEvudfYUmXKReyhTgIL/9OiTy8D8DqeQHT1hAZkp/ciG');

INSERT INTO user_roles (user_id, role)
SELECT id, 'MasterAdmin' FROM users WHERE user_id = 'admin'
UNION ALL
SELECT id, 'Sales'       FROM users WHERE user_id = 'sales'
UNION ALL
SELECT id, 'Router'      FROM users WHERE user_id = 'router'
UNION ALL
SELECT id, 'Tracker'     FROM users WHERE user_id = 'tracker'
UNION ALL
SELECT id, 'Handler'     FROM users WHERE user_id = 'handler'
UNION ALL
SELECT id, 'Accountant'  FROM users WHERE user_id = 'accountant'
UNION ALL
SELECT id, 'Shipper'     FROM users WHERE user_id = 'shipper'
UNION ALL
SELECT id, 'Consignee'   FROM users WHERE user_id = 'consignee';

-- migrate:down

DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE user_id IN ('admin','sales','router','tracker','handler','accountant','shipper','consignee'));
DELETE FROM users WHERE user_id IN ('admin','sales','router','tracker','handler','accountant','shipper','consignee');
