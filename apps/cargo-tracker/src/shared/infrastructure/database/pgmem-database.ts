import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { Kysely, PostgresDialect, CamelCasePlugin } from 'kysely';
import { newDb, type IMemoryDb } from 'pg-mem';
import type { Database } from './schema.js';
import type { AppDatabase } from './database.js';

const here = dirname(fileURLToPath(import.meta.url));
const MIGRATIONS_DIR = join(here, '../../../../migrations');

/**
 * migrations/ の .sql を昇順に適用した pg-mem 上の Kysely インスタンスを生成する。
 * ローカル開発・単体寄り統合テストのデフォルト DB（Docker 不要）。
 * SQL 互換性の正は Testcontainers（実 PostgreSQL）とする。
 */
export function createPgMemDatabase(): { db: AppDatabase; mem: IMemoryDb } {
  const mem = newDb();
  applyMigrations(mem);

  // pg-mem は pg 互換の { Pool } を提供する。Kysely の PostgresDialect にそのまま渡す。
  const pg = mem.adapters.createPg();
  const db = new Kysely<Database>({
    dialect: new PostgresDialect({
      pool: new pg.Pool(),
    }),
    plugins: [new CamelCasePlugin()],
  });

  return { db, mem };
}

function applyMigrations(mem: IMemoryDb): void {
  const files = readdirSync(MIGRATIONS_DIR)
    .filter((f) => f.endsWith('.sql'))
    .sort();
  for (const file of files) {
    const sql = readFileSync(join(MIGRATIONS_DIR, file), 'utf-8');
    mem.public.none(sql);
  }
}
