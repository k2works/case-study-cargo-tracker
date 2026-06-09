-- handling_type CHECK 制約追加（IT10 A4.2 / US33 / IT9 V5 バグ再発防止の横展開）
-- HandlingType enum（RECEIVE / LOAD / UNLOAD / CLAIM / CUSTOMS）と DB 値域を同期する。
-- 既存データは Java 側 enum 変換を経由しているため必ず enum 値であり、
-- 制約追加による既存行の違反は発生しない（V2 でコメント記載の値域と同一）。
-- 同期検証は BillingStatusCheckConstraintTest 同等のテスト
-- （HandlingTypeCheckConstraintTest）で CI 担保する。

ALTER TABLE handling_activity DROP CONSTRAINT IF EXISTS chk_handling_type;
ALTER TABLE handling_activity
    ADD CONSTRAINT chk_handling_type CHECK (
        handling_type IN ('RECEIVE', 'LOAD', 'UNLOAD', 'CLAIM', 'CUSTOMS')
    );
