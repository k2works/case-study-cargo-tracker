-- 監査ログを設計（data-model.md「auth_db」）の形に揃える（US31 §受入基準 7）。
--
-- V002 の形（id / event / succeeded）では「なぜ断ったか」を残せない。総当たりと
-- 打ち間違い、無効化アカウントへの試行が、すべて event='SIGN_IN', succeeded=false
-- で同じ行になる。利用者に返すメッセージは同一にする一方、記録では区別できな
-- ければならない。
--
-- **V002 は書き換えない。** 適用済みの環境では checksum が変わって起動しなくなる。
-- CI は緑のまま、既に動いているクラスタだけが落ちる。

ALTER TABLE auth_audit_log RENAME COLUMN id TO audit_id;
ALTER TABLE auth_audit_log RENAME COLUMN event TO event_type;

-- 既存行を新しい種別に読み替える。succeeded を落とす前に写す。
UPDATE auth_audit_log
   SET event_type = CASE WHEN succeeded THEN 'LOGIN_SUCCESS' ELSE 'LOGIN_FAILURE' END;

ALTER TABLE auth_audit_log DROP COLUMN succeeded;

-- 断った理由。画面には出さない。LOGIN_SUCCESS と UNLOCKED では NULL。
ALTER TABLE auth_audit_log ADD COLUMN reason VARCHAR(30);
