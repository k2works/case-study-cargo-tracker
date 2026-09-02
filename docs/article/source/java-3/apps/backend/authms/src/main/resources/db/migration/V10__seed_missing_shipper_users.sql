-- ログイン画面に載っているのに存在しなかった荷主利用者。
--
-- **画面に出ている ID でログインできないのは、利用者から見れば「壊れている」と同じ。**
-- shipper02 / shipper03 は US33 の確認用として画面（`demo-login.ts`）には載っていたが、
-- 種に入っておらず、実環境では選んでもログインできなかった。E2E は MSW の
-- モックを相手にしていたため緑のまま——**モックが本物より甘かった**。
--
-- 以後は `demo-login-accounts.test.ts` が、画面の名簿と種の食い違いを赤にする。
--
-- パスワードは "password"（BCrypt ハッシュ）。本番環境では適用しない。
INSERT INTO users (username, email, display_name, password, enabled) VALUES
    ('shipper02', 'shipper02@example.com', '未紐付け商事',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE),
    ('shipper03', 'shipper03@example.com', 'example 物流',
     '$2a$10$lTzWgdujlwNQ.Cl1SO5imOmysGCBAuwng6DxPKJGMXkDzmlH2lc.y', TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_SHIPPER' FROM users WHERE username IN ('shipper02', 'shipper03');

-- **どちらも荷主に紐付けない。**
--
-- shipper02 は紐付けが無いときの案内（US33-4）を確かめるための利用者であり、
-- 紐付けると確認できなくなる。
--
-- shipper03 も紐付けない。**紐付け先を番号で決め打ちできないため**である
-- ——bookingms は荷主の種を持たず、`shipper` の 1 番が誰になるかは
-- その環境で最初に登録された荷主で決まる（実際、DB を初期化した直後の 1 番は
-- シミュレーションが作った荷主だった）。紐付けは管理者の操作
-- （`PUT /api/v1/admin/user-shipper-links/{username}`）で行う。
