-- 共有のスキーマとドメイン型（ADR-008）。Unit のスキーマ（booking など）は各 Unit のマイグレーションが作る。
-- 値オブジェクトの検証を DB でも二重に守る（安い検証だけ。状態遷移はドメインだけが守る）。

CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS reference;

-- 集約・行の ID（ULID。Crockford Base32 で、先頭は 7 以下）
CREATE DOMAIN platform.ulid AS char(26)
  CHECK (VALUE ~ '^[0-7][0-9A-HJKMNP-TV-Z]{25}$');

-- UN/LOCODE（形式のみ。実在は reference.port の外部キーが保証する）
CREATE DOMAIN platform.port_code AS char(5)
  CHECK (VALUE ~ '^[A-Z]{2}[A-Z2-9]{3}$');

-- 貨物種別
CREATE DOMAIN platform.cargo_kind AS text
  CHECK (VALUE IN ('GENERAL', 'DANGEROUS', 'TEMPERATURE_CONTROLLED'));
