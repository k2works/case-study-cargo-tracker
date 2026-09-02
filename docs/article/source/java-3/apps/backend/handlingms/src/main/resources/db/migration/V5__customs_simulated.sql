-- 通関申告の由来（[ADR-030] 決定 3・TD-02・IT16）。
--
-- **追跡管理者の朝の仕事は「未決着を上から片付ける」ことである。** 架空の申告が
-- 並ぶと、実在の貨物が後ろへ押し出される。
--
-- **列に持つ。** handlingms は荷主を持たないため、由来は bookingms に問うしかない。
-- 取得後にアプリ側で絞ると、**件数と上限が壊れる**——「12 件あります」と出るのに
-- 開くと 3 件、という形になる。登録の時点で受け取り、SQL で絞れるようにする。
--
-- 既存の行は実業務として扱う。列が無かったころの申告に由来は無く、
-- **後から推測して埋めると、実在の申告を待ち行列から消しかねない**。
ALTER TABLE customs_declaration ADD COLUMN simulated BOOLEAN;

UPDATE customs_declaration SET simulated = FALSE WHERE simulated IS NULL;

ALTER TABLE customs_declaration ALTER COLUMN simulated SET NOT NULL;

-- 待ち行列は「未決着かつ実業務」で引く
CREATE INDEX idx_customs_declaration_simulated ON customs_declaration (simulated, status, declared_at);
