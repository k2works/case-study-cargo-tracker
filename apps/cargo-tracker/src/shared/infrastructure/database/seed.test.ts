import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createPgMemDatabase } from './pgmem-database.js';
import type { AppDatabase } from './database.js';
import { seedDefaultUsers } from './seed.js';

describe('seedDefaultUsers', () => {
  let db: AppDatabase;

  beforeEach(() => {
    db = createPgMemDatabase().db;
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('6 ロール分のユーザーを投入する', async () => {
    await seedDefaultUsers(db);
    const users = await db.selectFrom('users').selectAll().execute();
    expect(users).toHaveLength(6);
    const roles = await db.selectFrom('user_roles').selectAll().execute();
    expect(roles).toHaveLength(6);
  });

  it('冪等: 2 回実行してもユーザーは重複しない', async () => {
    await seedDefaultUsers(db);
    await seedDefaultUsers(db);
    const users = await db.selectFrom('users').selectAll().execute();
    expect(users).toHaveLength(6);
  });
});
