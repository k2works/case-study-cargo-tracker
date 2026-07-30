-- IT5: leg の load_time / unload_time を NOT NULL 化（IT4 Try T3）
-- 出典: docs/development/iteration_plan-5.md タスク 1.4、docs/review/IT4実装_review_20260729.md（architect L3 / programmer L2）。
-- 書き込み時は必ず値が入るため、nullable のままだと reconstruct のフォールバックがデータ破損を隠蔽する。

-- Up Migration

-- 書き込み経路（leg 入替トランザクション）は常に時刻を設定するため、既存 NULL 行は存在しない前提。
-- NULL 行が存在する環境では本マイグレーションが失敗して不正データを顕在化させる（サイレント補完はしない）。
ALTER TABLE leg ALTER COLUMN load_time SET NOT NULL;
ALTER TABLE leg ALTER COLUMN unload_time SET NOT NULL;
