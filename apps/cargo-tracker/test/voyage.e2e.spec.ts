import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

describe('航海スケジュール管理フロー (US24/US25/US07)', () => {
  let ctx: TestApp;
  let router: TestAgent;

  beforeEach(async () => {
    ctx = await createTestApp();
    await seedLocations();
    router = await loginAsTestUser(ctx, { username: 'router1', roles: [Role.ROUTE_DESIGNER] });
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('経路設計者は航海一覧と登録画面に到達できる', async () => {
    const index = await router.get('/voyages');
    expect(index.status).toBe(200);
    expect(index.text).toContain('航海スケジュール一覧');
    expect(index.text).toContain('航海番号');
    expect(index.text).toContain('/voyages/new');

    const form = await router.get('/voyages/new');
    expect(form.status).toBe(200);
    expect(form.text).toContain('航海スケジュール登録');
    expect(form.text).toContain('対応貨物種別');
    expect(form.text).toContain('出発港');
  });

  it('航海スケジュールを登録すると一覧の検索対象に出る', async () => {
    const create = await router.post('/voyages').type('form').send({
      voyageNumber: 'V001',
      shipName: 'Pacific Star',
      carrierName: 'Oceanic',
      supportedCargoTypes: ['GENERAL', 'REFRIGERATED'],
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-01T09:00',
      arrivalTime: '2026-09-08T08:00',
    });

    expect(create.status).toBe(302);
    expect(create.headers.location).toBe('/voyages');

    const index = await router.get('/voyages');
    expect(index.text).toContain('V001');
    expect(index.text).toContain('Pacific Star');
    expect(index.text).toContain('Oceanic');
    expect(index.text).toContain('JPTYO');
    expect(index.text).toContain('SGSIN');
  });

  it('既存航海スケジュールの更新フォームに到達し日程を更新できる', async () => {
    await registerVoyage('V002');

    const edit = await router.get('/voyages/V002/edit');
    expect(edit.status).toBe(200);
    expect(edit.text).toContain('航海スケジュール更新');
    expect(edit.text).toContain('V002');

    const update = await router.post('/voyages/V002').type('form').send({
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-03T09:00',
      arrivalTime: '2026-09-10T08:00',
    });
    expect(update.status).toBe(302);
    expect(update.headers.location).toBe('/voyages');

    const index = await router.get('/voyages');
    expect(index.text).toContain('V002');
    expect(index.text).toContain('2026-09-10');
  });

  it('検索条件で航海スケジュールを絞り込み htmx フラグメントを返す', async () => {
    await registerVoyage('V003');
    await router.post('/voyages').type('form').send({
      voyageNumber: 'V004',
      shipName: 'Hong Kong Shuttle',
      carrierName: 'Oceanic',
      supportedCargoTypes: ['GENERAL'],
      departureLocation: 'JPTYO',
      arrivalLocation: 'HKHKG',
      departureTime: '2026-09-01T09:00',
      arrivalTime: '2026-09-04T08:00',
    });

    const filtered = await router.get('/voyages?destination=SGSIN');
    expect(filtered.text).toContain('V003');
    expect(filtered.text).not.toContain('V004');

    const fragment = await router.get('/voyages?destination=SGSIN').set('HX-Request', 'true');
    expect(fragment.status).toBe(200);
    expect(fragment.text).toContain('data-testid="voyage-list"');
    expect(fragment.text).toContain('V003');
    expect(fragment.text).not.toContain('<html');
  });

  it('貨物種別で対応可能な航海だけに絞り込む', async () => {
    await registerVoyage('V005');
    await router.post('/voyages').type('form').send({
      voyageNumber: 'V006',
      shipName: 'Hazard Carrier',
      carrierName: 'Oceanic',
      supportedCargoTypes: ['HAZARDOUS'],
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-02T09:00',
      arrivalTime: '2026-09-09T08:00',
    });

    const filtered = await router.get('/voyages?cargoType=HAZARDOUS');
    expect(filtered.text).toContain('V006');
    expect(filtered.text).not.toContain('V005');
  });

  async function seedLocations(): Promise<void> {
    await ctx.db
      .insertInto('location')
      .values([
        { unlocode: 'JPTYO', name: 'Tokyo' },
        { unlocode: 'SGSIN', name: 'Singapore' },
        { unlocode: 'HKHKG', name: 'Hong Kong' },
      ])
      .execute();
  }

  async function registerVoyage(voyageNumber: string): Promise<void> {
    await router.post('/voyages').type('form').send({
      voyageNumber,
      shipName: 'Pacific Star',
      carrierName: 'Oceanic',
      supportedCargoTypes: ['GENERAL'],
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-01T09:00',
      arrivalTime: '2026-09-08T08:00',
    });
  }
});
