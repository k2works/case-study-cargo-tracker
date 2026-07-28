import { Kysely, PostgresDialect, CamelCasePlugin } from 'kysely';
import { Pool } from 'pg';
import type { Database } from './schema.js';

/** Kysely インスタンス型（アプリ全体で共有する DB ハンドル） */
export type AppDatabase = Kysely<Database>;

/** DI トークン。合成ルートで実装（pg / pg-mem）を束縛する */
export const DATABASE = Symbol('DATABASE');

/**
 * 実 PostgreSQL 接続の Kysely インスタンスを生成する。
 * 本番・Testcontainers で使用する。
 */
export function createPostgresDatabase(connectionString: string): AppDatabase {
  return new Kysely<Database>({
    dialect: new PostgresDialect({
      pool: new Pool({ connectionString }),
    }),
    plugins: [new CamelCasePlugin()],
  });
}
