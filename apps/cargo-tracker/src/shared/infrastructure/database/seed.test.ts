import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createPgMemDatabase } from './pgmem-database.js';
import type { AppDatabase } from './database.js';
import { seedAll, seedDefaultUsers, seedLocations, seedShippers, seedVoyages } from './seed.js';

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

  it('location マスタを投入する（冪等）', async () => {
    await seedLocations(db);
    await seedLocations(db);
    const locations = await db.selectFrom('location').selectAll().execute();
    expect(locations).toHaveLength(10);
    expect(locations.map((l) => l.unlocode)).toContain('JPTYO');
  });

  it('荷主マスタを個人 2・法人 2 で投入する（冪等・法人は割引率あり）', async () => {
    await seedShippers(db);
    await seedShippers(db);
    const shippers = await db.selectFrom('shipper').selectAll().orderBy('shipperCode', 'asc').execute();
    expect(shippers).toHaveLength(4);
    expect(shippers.filter((s) => s.shipperType === 'CORPORATE')).toHaveLength(2);
    // 法人は契約割引率（US22）を持つ
    const corporate = shippers.filter((s) => s.shipperType === 'CORPORATE');
    expect(corporate.every((s) => Number(s.discountRate) > 0)).toBe(true);
    // 個人は割引なし
    const individual = shippers.filter((s) => s.shipperType === 'INDIVIDUAL');
    expect(individual.every((s) => Number(s.discountRate) === 0)).toBe(true);
  });

  it('航海スケジュールを投入する（冪等・now 基準の相対日程で期限内候補になる）', async () => {
    const now = new Date('2026-08-01T00:00:00Z');
    // carrier_movement は location への FK を持つため先にマスタを投入する
    await seedLocations(db);
    await seedVoyages(db, now);
    await seedVoyages(db, now);
    const voyages = await db.selectFrom('voyage').selectAll().execute();
    expect(voyages).toHaveLength(2);
    expect(voyages.map((v) => v.voyageNumber)).toContain('V-DEMO-001');
    // 直行 1 区間 + 経由 2 区間 = 3 運送区間
    const movements = await db.selectFrom('carrier_movement').selectAll().execute();
    expect(movements).toHaveLength(3);
    // 日程は now より未来（予約期限を十分取れば期限内候補になる）
    expect(movements.every((m) => new Date(m.departureDate) > now)).toBe(true);
  });

  it('seedAll: 業務フロー用シードを一括投入する（冪等）', async () => {
    const now = new Date('2026-08-01T00:00:00Z');
    await seedAll(db, now);
    await seedAll(db, now);
    expect(await db.selectFrom('users').selectAll().execute()).toHaveLength(6);
    expect(await db.selectFrom('location').selectAll().execute()).toHaveLength(10);
    expect(await db.selectFrom('shipper').selectAll().execute()).toHaveLength(4);
    expect(await db.selectFrom('voyage').selectAll().execute()).toHaveLength(2);
    expect(await db.selectFrom('carrier_movement').selectAll().execute()).toHaveLength(3);
  });
});
