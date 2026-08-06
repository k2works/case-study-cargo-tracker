-- 荷主メールの一意制約を大文字小文字に依存しない形へ（IT4 のレビュー指摘）
--
-- **問題**: V4 の `email VARCHAR(200) NOT NULL UNIQUE` は大文字小文字を区別する。
-- 一方でアプリケーション層の重複判定は `LOWER(email)` で行っている。
-- アプリが正規化して守っている不変条件を DB のバックストップがカバーしておらず、
-- `yamada@example.com` と `YAMADA@EXAMPLE.COM` が同時に投入されると
-- **両方とも INSERT に成功して同じ人の二重登録が生まれる**
-- （アプリ層の check-then-act は同時実行で抜ける）。
--
-- **式インデックス（`CREATE UNIQUE INDEX ... ON shipper (LOWER(email))`）を使わない**。
-- PostgreSQL では動くが H2 は解釈できず、テスト環境で適用に失敗する。
-- 「どちらか一方でしか動かない構文を書いた時点で、片方の環境は検証されていない」
-- （R__location_master.sql と同じ教訓）。
--
-- 代わりに正規化した値を列として持つ。標準的な DDL のみで両方の DB に適用できる。
-- 値の生成はアプリケーション層が行う（生成列の構文は方言差が大きい）。

ALTER TABLE shipper ADD COLUMN email_normalized VARCHAR(200);

UPDATE shipper SET email_normalized = LOWER(email);

ALTER TABLE shipper ALTER COLUMN email_normalized SET NOT NULL;

CREATE UNIQUE INDEX ux_shipper_email_normalized ON shipper (email_normalized);

-- V4 の `idx_shipper_email` は UNIQUE 制約が張る索引と重複しており、
-- 更新コストを二重に払うだけで引く側の利点がない。
DROP INDEX IF EXISTS idx_shipper_email;
