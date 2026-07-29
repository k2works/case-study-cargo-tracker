import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';
import { CargoType } from '../src/shared/domain/model/cargo-type.js';

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

  it('既存航海スケジュールの差分を確認して日程を更新できる', async () => {
    await registerVoyage('V002');

    const edit = await router.get('/voyages/V002/edit');
    expect(edit.status).toBe(200);
    expect(edit.text).toContain('航海スケジュール更新');
    expect(edit.text).toContain('V002');

    const confirm = await router.post('/voyages/V002/confirm').type('form').send({
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-03T09:00',
      arrivalTime: '2026-09-10T08:00',
    });
    expect(confirm.status).toBe(200);
    expect(confirm.text).toContain('航海スケジュール更新確認');
    expect(confirm.text).toContain('2026-09-01');
    expect(confirm.text).toContain('2026-09-03');
    expect(confirm.text).toContain('更新する');
    expect(confirm.text).toContain('キャンセル');

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

  it('既存航海スケジュール更新をキャンセルすると日程を変更しない', async () => {
    await registerVoyage('V010');

    const confirm = await router.post('/voyages/V010/confirm').type('form').send({
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-03T09:00',
      arrivalTime: '2026-09-10T08:00',
    });
    expect(confirm.status).toBe(200);

    const cancel = await router.post('/voyages/V010/cancel').type('form').send({
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-03T09:00',
      arrivalTime: '2026-09-10T08:00',
    });
    expect(cancel.status).toBe(302);
    expect(cancel.headers.location).toBe('/voyages');

    const index = await router.get('/voyages');
    expect(index.text).toContain('V010');
    expect(index.text).toContain('2026-09-08');
    expect(index.text).not.toContain('2026-09-10');
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

  it('予約番号指定時に経路設計待ち予約の条件を検索条件へ引き継ぐ', async () => {
    const bookingId = await seedRoutingInProgressBooking();
    await registerVoyage('V007');
    await router.post('/voyages').type('form').send({
      voyageNumber: 'V008',
      shipName: 'Different Route',
      carrierName: 'Oceanic',
      supportedCargoTypes: ['GENERAL'],
      departureLocation: 'JPTYO',
      arrivalLocation: 'HKHKG',
      departureTime: '2026-09-01T09:00',
      arrivalTime: '2026-09-04T08:00',
    });

    const res = await router.get(`/voyages?bookingId=${bookingId}`);
    expect(res.status).toBe(200);
    expect(res.text).toContain('予約条件');
    expect(res.text).toContain(bookingId.slice(0, 8));
    expect(res.text).toContain('JPTYO');
    expect(res.text).toContain('SGSIN');
    expect(res.text).toContain('一般貨物');
    expect(res.text).toContain('V007');
    expect(res.text).not.toContain('V008');
  });

  it('/routing/candidates htmx フラグメントで経路候補テーブルを返す', async () => {
    await registerVoyage('V009');

    const res = await router
      .get(
        '/routing/candidates?origin=JPTYO&destination=SGSIN&arrivalDeadline=2026-09-10&cargoType=GENERAL',
      )
      .set('HX-Request', 'true');

    expect(res.status).toBe(200);
    expect(res.text).toContain('data-testid="route-candidate-list"');
    expect(res.text).toContain('V009');
    expect(res.text).toContain('所要日数');
    expect(res.text).toContain('経由港');
    expect(res.text).toContain('費用');
    expect(res.text).not.toContain('<html');
  });

  it('IT3 デモ: 航海登録から検索、経路候補算出まで縦貫通する', async () => {
    const create = await router.post('/voyages').type('form').send({
      voyageNumber: 'DEMO-001',
      shipName: 'Demo Star',
      carrierName: 'Demo Carrier',
      supportedCargoTypes: ['GENERAL'],
      departureLocation: 'JPTYO',
      arrivalLocation: 'SGSIN',
      departureTime: '2026-09-01T09:00',
      arrivalTime: '2026-09-08T08:00',
    });
    expect(create.status).toBe(302);

    const search = await router.get('/voyages?origin=JPTYO&destination=SGSIN&cargoType=GENERAL');
    expect(search.status).toBe(200);
    expect(search.text).toContain('DEMO-001');

    const candidates = await router
      .get(
        '/routing/candidates?origin=JPTYO&destination=SGSIN&arrivalDeadline=2026-09-10&cargoType=GENERAL',
      )
      .set('HX-Request', 'true');
    expect(candidates.status).toBe(200);
    expect(candidates.text).toContain('DEMO-001');
    expect(candidates.text).toContain('直行');
    expect(candidates.text).toContain('7 日');
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

  async function seedRoutingInProgressBooking(): Promise<string> {
    const shipper = await ctx.db
      .insertInto('shipper')
      .values({
        shipperCode: 'SHP-route001',
        shipperType: 'INDIVIDUAL',
        name: '経路荷主',
        email: 'route-shipper@example.com',
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    const bookingId = '11111111-1111-4111-8111-111111111111';
    await ctx.db
      .insertInto('cargo')
      .values({
        bookingId,
        shipperId: shipper.id,
        cargoType: CargoType.GENERAL,
        weight: 1200,
        originUnlocode: 'JPTYO',
        destinationUnlocode: 'SGSIN',
        arrivalDeadline: '2026-09-30',
        bookingStatus: 'ROUTING_IN_PROGRESS',
        consigneeName: '受取花子',
        consigneeEmail: 'uke@example.com',
        consigneeAddress: '大阪市北区',
      })
      .execute();
    return bookingId;
  }
});
