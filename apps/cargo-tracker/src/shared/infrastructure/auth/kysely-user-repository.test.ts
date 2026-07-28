import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Role } from '../../domain/model/role.js';
import { createPgMemDatabase } from '../database/pgmem-database.js';
import type { AppDatabase } from '../database/database.js';
import { KyselyUserRepository } from './kysely-user-repository.js';

describe('KyselyUserRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyUserRepository;

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    repo = new KyselyUserRepository(db);
    const user = await db
      .insertInto('users')
      .values({ username: 'sales1', email: 'sales1@example.com', password: 'HASH' })
      .returning('id')
      .executeTakeFirstOrThrow();
    await db
      .insertInto('user_roles')
      .values([
        { userId: user.id, role: Role.SALES },
        { userId: user.id, role: Role.SHIPPER },
      ])
      .execute();
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('findByUsername でロール込みのアカウントを取得する', async () => {
    const account = await repo.findByUsername('sales1');
    expect(account).not.toBeNull();
    expect(account?.username).toBe('sales1');
    expect(account?.passwordHash).toBe('HASH');
    expect(account?.enabled).toBe(true);
    expect(account?.failedAttempts).toBe(0);
    expect(account?.roles).toEqual(expect.arrayContaining([Role.SALES, Role.SHIPPER]));
  });

  it('存在しないユーザーは null を返す', async () => {
    expect(await repo.findByUsername('ghost')).toBeNull();
  });

  it('incrementFailedAttempts で失敗回数が加算される', async () => {
    const account = await repo.findByUsername('sales1');
    await repo.incrementFailedAttempts(account!.id);
    const after = await repo.findByUsername('sales1');
    expect(after?.failedAttempts).toBe(1);
  });

  it('resetFailedAttempts で失敗回数が 0 に戻る', async () => {
    const account = await repo.findByUsername('sales1');
    await repo.incrementFailedAttempts(account!.id);
    await repo.resetFailedAttempts(account!.id);
    const after = await repo.findByUsername('sales1');
    expect(after?.failedAttempts).toBe(0);
  });
});
