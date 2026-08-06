import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';
import type { AppDatabase } from '../src/shared/infrastructure/database/database.js';

async function seedShipper(db: AppDatabase, code: string): Promise<void> {
  await db
    .insertInto('shipper')
    .values({ shipperCode: code, shipperType: 'INDIVIDUAL', name: '荷主太郎', email: `${code}@example.com` })
    .execute();
}

function futureDate(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

describe('貨物予約フロー (US04/US05/US06)', () => {
  let ctx: TestApp;
  let sales: TestAgent;

  beforeEach(async () => {
    ctx = await createTestApp();
    await seedShipper(ctx.db, 'SHP-abc12345');
    sales = await loginAsTestUser(ctx, { username: 'sales1', roles: [Role.SALES] });
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  const baseBooking = () => ({
    shipperCode: 'SHP-abc12345',
    consigneeName: '受取花子',
    consigneeEmail: 'uke@example.com',
    consigneeAddress: '大阪市北区',
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: futureDate(45),
    weightKg: '1200',
    cargoType: 'GENERAL',
  });

  it('営業担当者は予約登録画面に到達できる', async () => {
    const res = await sales.get('/bookings/new');
    expect(res.status).toBe(200);
    expect(res.text).toContain('貨物予約登録');
    expect(res.text).toContain('荷主 ID');
    expect(res.text).toContain('荷受人');
  });

  it('一般貨物を登録すると仮受付で予約番号が発行される（US04）', async () => {
    const create = await sales.post('/bookings').type('form').send(baseBooking());
    expect(create.status).toBe(302);
    expect(create.headers.location).toMatch(/^\/bookings\/[0-9a-f-]{36}$/);

    const show = await sales.get(create.headers.location!);
    expect(show.text).toContain('予約番号');
    expect(show.text).toContain('仮受付');
    expect(show.text).toContain('受取花子');

    const saved = await ctx.db.selectFrom('cargo').selectAll().executeTakeFirst();
    expect(saved?.bookingStatus).toBe('PRELIMINARY');
    expect(saved?.consigneeName).toBe('受取花子');
  });

  it('存在しない荷主 ID はエラー（US04 荷主存在確認）', async () => {
    const res = await sales.post('/bookings').type('form').send({ ...baseBooking(), shipperCode: 'SHP-nope0000' });
    expect(res.status).toBe(200);
    expect(res.text).toContain('該当する荷主が見つかりません');
  });

  it('危険物は申告必須（htmx フィールド + 検証）（US05）', async () => {
    const frag = await sales.get('/bookings/fields?cargoType=HAZARDOUS');
    expect(frag.text).toContain('危険物クラス');

    const ok = await sales.post('/bookings').type('form').send({
      ...baseBooking(),
      cargoType: 'HAZARDOUS',
      hazardousClass: '3',
      unNumber: 'UN1203',
      properShippingName: 'Gasoline',
    });
    expect(ok.status).toBe(302);
    const saved = await ctx.db.selectFrom('cargo').selectAll().executeTakeFirst();
    expect(saved?.hazardousClass).toBe('3');
  });

  it('冷凍貨物は温度管理条件が必須（US05）', async () => {
    const frag = await sales.get('/bookings/fields?cargoType=REFRIGERATED');
    expect(frag.text).toContain('最低温度');

    await sales.post('/bookings').type('form').send({
      ...baseBooking(),
      cargoType: 'REFRIGERATED',
      minTemperature: '-20',
      maxTemperature: '-5',
      temperatureUnit: 'CELSIUS',
    });
    const saved = await ctx.db.selectFrom('cargo').selectAll().executeTakeFirst();
    expect(saved?.cargoType).toBe('REFRIGERATED');
    expect(Number(saved?.minTemperature)).toBe(-20);
  });

  it('危険物で申告を空にすると検証エラーで保存されない（US05 否定パス）', async () => {
    const res = await sales.post('/bookings').type('form').send({
      ...baseBooking(),
      cargoType: 'HAZARDOUS',
      hazardousClass: '',
      unNumber: '',
      properShippingName: '',
    });
    expect(res.status).toBe(200);
    expect(res.text).toContain('危険物申告');
    const saved = await ctx.db.selectFrom('cargo').selectAll().execute();
    expect(saved).toHaveLength(0);
  });

  it('冷凍で温度を空にすると検証エラーで保存されない（US05 否定パス）', async () => {
    const res = await sales.post('/bookings').type('form').send({
      ...baseBooking(),
      cargoType: 'REFRIGERATED',
      minTemperature: '',
      maxTemperature: '',
    });
    expect(res.status).toBe(200);
    const saved = await ctx.db.selectFrom('cargo').selectAll().execute();
    expect(saved).toHaveLength(0);
  });

  it('不正な UN/LOCODE は検証エラー（内部障害扱いにしない）', async () => {
    const res = await sales.post('/bookings').type('form').send({ ...baseBooking(), origin: 'JPT' });
    expect(res.status).toBe(200);
    expect(res.text).toContain('UN/LOCODE');
  });

  it('重量 0 は検証エラーで保存されない', async () => {
    const res = await sales.post('/bookings').type('form').send({ ...baseBooking(), weightKg: '0' });
    expect(res.status).toBe(200);
    expect(res.text).toContain('重量');
    const saved = await ctx.db.selectFrom('cargo').selectAll().execute();
    expect(saved).toHaveLength(0);
  });

  it('経路設計者へ引き渡すと ROUTING_IN_PROGRESS になり待ち一覧に出る（US06）', async () => {
    const create = await sales.post('/bookings').type('form').send(baseBooking());
    const bookingUrl = create.headers.location!;
    const bookingId = bookingUrl.split('/').pop()!;

    const assign = await sales.post(`/bookings/${bookingId}/assign-to-routing`);
    expect(assign.status).toBe(302);

    const show = await sales.get(bookingUrl);
    expect(show.text).toContain('経路設計中');

    // 経路設計者が待ち一覧で到達できる
    const router = await loginAsTestUser(ctx, { username: 'router1', roles: [Role.ROUTE_DESIGNER] });
    const list = await router.get('/bookings?status=ROUTING_IN_PROGRESS');
    expect(list.status).toBe(200);
    expect(list.text).toContain('経路設計待ち予約');
    expect(list.text).toContain(bookingId.slice(0, 8));
  });
});
