-- アカウントロックの状態を保持する（US31）。
--
-- **ロック状態は導出せず永続化する。** 失敗回数を認証ログから数え直す設計にすると、
-- ユニットテストが緑でもリクエストをまたいだ時に誤判定する。
-- 「5 回連続失敗でロック」はリクエストを越えて成立しなければならない不変条件である。
ALTER TABLE users ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
