import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createPgMemDatabase } from './pgmem-database.js';
import type { AppDatabase } from './database.js';

describe('createPgMemDatabase（マイグレーション適用済み pg-mem）', () => {
  let db: AppDatabase;

  beforeEach(() => {
    db = createPgMemDatabase().db;
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('shipper テーブルへ INSERT / SELECT できる', async () => {
    await db
      .insertInto('shipper')
      .values({
        shipperCode: 'SHP-ABCD1234',
        shipperType: 'INDIVIDUAL',
        name: '山田太郎',
        email: 'yamada@example.com',
        phone: null,
        contractNumber: null,
        discountRate: 0,
      })
      .execute();

    const rows = await db.selectFrom('shipper').selectAll().execute();
    expect(rows).toHaveLength(1);
    expect(rows[0].shipperCode).toBe('SHP-ABCD1234');
  });

  it('users / user_roles テーブルが存在する', async () => {
    const inserted = await db
      .insertInto('users')
      .values({ username: 'sales1', email: 'sales1@example.com', password: 'hashed' })
      .returning('id')
      .executeTakeFirstOrThrow();

    await db
      .insertInto('user_roles')
      .values({ userId: inserted.id, role: 'ROLE_SALES' })
      .execute();

    const roles = await db.selectFrom('user_roles').selectAll().execute();
    expect(roles).toHaveLength(1);
    expect(roles[0].role).toBe('ROLE_SALES');
  });
});
