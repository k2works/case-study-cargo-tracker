import { createPostgresDatabase } from './database.js';
import { seedAll } from './seed.js';

/**
 * シードコマンド実行タスク。
 * DATABASE_URL で指定した実 PostgreSQL に業務フロー用シードデータを投入する。
 * 各シード関数は冪等なので、複数回実行しても重複しない。
 *
 *   DATABASE_URL=postgres://... npm run seed
 */
async function main(): Promise<void> {
  const connectionString = process.env.DATABASE_URL;
  if (connectionString === undefined || connectionString === '') {
    throw new Error('DATABASE_URL を設定してください（例: DATABASE_URL=postgres://user:pass@host:5432/db npm run seed）');
  }
  const db = createPostgresDatabase(connectionString);
  try {
    await seedAll(db);
    // eslint-disable-next-line no-console
    console.log('シードデータの投入が完了しました（ユーザー・ロケーション・荷主・航海）。');
  } finally {
    await db.destroy();
  }
}

void main().catch((error: unknown) => {
  // eslint-disable-next-line no-console
  console.error('シードデータの投入に失敗しました:', error);
  process.exitCode = 1;
});
