import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser, waitUntil } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * IT6 通関フロー（US16 前提・ADR-010）。
 * 通関ステータス画面（/tracking/{tn}/customs）で申告登録 → 状態更新（通関済/留置/不可）を
 * 対象ロール（追跡管理者・荷役作業員）が画面操作で完結でき、HELD で CUSTOMS_HOLD 例外が
 * 自動登録される（イベント連携・冪等）ことを検証する。
 */
describe('通関フロー (US16 前提・ADR-010)', () => {
  let ctx: TestApp;
  let sales: TestAgent;
  let router: TestAgent;
  let handler: TestAgent;
  let tracker: TestAgent;

  beforeEach(async () => {
    ctx = await createTestApp();
    await seedLocations();
    sales = await loginAsTestUser(ctx, { username: 'sales1', roles: [Role.SALES] });
    router = await loginAsTestUser(ctx, { username: 'router1', roles: [Role.ROUTE_DESIGNER] });
    handler = await loginAsTestUser(ctx, { username: 'handler1', roles: [Role.HANDLER] });
    tracker = await loginAsTestUser(ctx, { username: 'tracker1', roles: [Role.TRACKER] });
    await seedShipper();
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('荷役作業員が通関申告を登録し、CLEARED にすると通関済になる（US16 前提）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    // 荷役一覧から通関ステータスへ到達できる（ロール別到達性）
    const list = await handler.get('/handling');
    expect(list.text).toContain('通関');

    // 申告なし状態
    let page = await handler.get(`/tracking/${trackingNumber}/customs`);
    expect(page.status).toBe(200);
    expect(page.text).toContain('申告なし');

    // 申告登録（PENDING）
    const register = await handler.post(`/tracking/${trackingNumber}/customs`).type('form').send({ remarks: 'テスト申告' });
    expect(register.status).toBe(302);
    let declaration = await ctx.db.selectFrom('customs_declaration').selectAll().executeTakeFirstOrThrow();
    expect(declaration.status).toBe('PENDING');

    // 一覧に審査中で表示される
    page = await handler.get(`/tracking/${trackingNumber}/customs`);
    expect(page.text).toContain('審査中');

    // CLEARED へ更新
    const clear = await handler
      .post(`/tracking/${trackingNumber}/customs/${declaration.declarationNumber}/status`)
      .type('form')
      .send({ status: 'CLEARED' });
    expect(clear.status).toBe(302);
    declaration = await ctx.db.selectFrom('customs_declaration').selectAll().executeTakeFirstOrThrow();
    expect(declaration.status).toBe('CLEARED');
    expect(declaration.clearedAt).not.toBeNull();
  });

  it('追跡管理者が通関 HELD にすると CUSTOMS_HOLD 例外が自動登録され EXCEPTION になる（ADR-010）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    // 追跡詳細から通関ステータスへ到達できる
    const detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('通関ステータス');

    await tracker.post(`/tracking/${trackingNumber}/customs`).type('form').send({});
    const declaration = await ctx.db.selectFrom('customs_declaration').selectAll().executeTakeFirstOrThrow();

    const held = await tracker
      .post(`/tracking/${trackingNumber}/customs/${declaration.declarationNumber}/status`)
      .type('form')
      .send({ status: 'HELD' });
    expect(held.status).toBe(302);
    await waitForEvents();

    // CUSTOMS_HOLD 例外が登録され、貨物状態が EXCEPTION になる
    const exceptions = await ctx.db.selectFrom('tracking_exception_event').selectAll().execute();
    const customsHolds = exceptions.filter((e) => e.exceptionType === 'CUSTOMS_HOLD');
    expect(customsHolds).toHaveLength(1);
    expect(customsHolds[0].description).toContain(declaration.declarationNumber);
    const activity = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(activity.transportStatus).toBe('EXCEPTION');
  });

  it('複数の通関申告を HELD にしても未解決の CUSTOMS_HOLD 例外は 1 件（冪等）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    for (let i = 0; i < 2; i += 1) {
      await tracker.post(`/tracking/${trackingNumber}/customs`).type('form').send({});
    }
    const declarations = await ctx.db.selectFrom('customs_declaration').selectAll().execute();
    expect(declarations.length).toBe(2);
    for (const declaration of declarations) {
      await tracker
        .post(`/tracking/${trackingNumber}/customs/${declaration.declarationNumber}/status`)
        .type('form')
        .send({ status: 'HELD' });
    }
    await waitForEvents();
    const customsHolds = (await ctx.db.selectFrom('tracking_exception_event').selectAll().execute()).filter(
      (e) => e.exceptionType === 'CUSTOMS_HOLD',
    );
    expect(customsHolds).toHaveLength(1);
  });

  it('ロール制御: 営業は通関ステータスにアクセスできない', async () => {
    const { trackingNumber } = await issueTracking();
    const res = await sales.get(`/tracking/${trackingNumber}/customs`);
    expect(res.status).toBe(403);
  });

  /** コミット後 fire-and-forget イベント（customs.held → CUSTOMS_HOLD 登録）の伝播を待つ（ADR-009） */
  /** CUSTOMS_HOLD 例外行が現れるまでポーリングする（fire-and-forget 伝播・ADR-009） */
  async function waitForEvents(): Promise<void> {
    await waitUntil(async () => {
      const rows = await ctx.db.selectFrom('tracking_exception_event').select('id').where('exceptionType', '=', 'CUSTOMS_HOLD').execute();
      return rows.length > 0;
    });
  }

  async function seedLocations(): Promise<void> {
    await ctx.db
      .insertInto('location')
      .values([
        { unlocode: 'JPTYO', name: 'Tokyo' },
        { unlocode: 'USLAX', name: 'Los Angeles' },
        { unlocode: 'HKHKG', name: 'Hong Kong' },
      ])
      .execute();
  }

  async function seedShipper(): Promise<void> {
    await ctx.db
      .insertInto('shipper')
      .values({ shipperCode: 'SHP-abc12345', shipperType: 'INDIVIDUAL', name: '荷主', email: 's@example.com' })
      .execute();
  }

  async function registerHandling(fields: Record<string, string>) {
    return handler.post('/handling').type('form').send(fields);
  }

  async function issueTracking(): Promise<{ bookingId: string; trackingNumber: string }> {
    await router.post('/voyages').type('form').send({
      voyageNumber: 'V001',
      shipName: 'Pacific Star',
      carrierName: 'Oceanic',
      supportedCargoTypes: ['GENERAL'],
      departureLocation: 'JPTYO',
      arrivalLocation: 'USLAX',
      departureTime: '2026-09-01T09:00',
      arrivalTime: '2026-09-15T08:00',
    });
    const create = await sales
      .post('/bookings')
      .type('form')
      .send({
        shipperCode: 'SHP-abc12345',
        consigneeName: '受取花子',
        consigneeEmail: 'uke@example.com',
        consigneeAddress: '大阪市北区',
        origin: 'JPTYO',
        destination: 'USLAX',
        arrivalDeadline: '2026-09-30',
        weightKg: '1200',
        cargoType: 'GENERAL',
      });
    const bookingId = create.headers.location!.replace('/bookings/', '');
    await sales.post(`/bookings/${bookingId}/assign-to-routing`);
    const routePage = await router.get(`/bookings/${bookingId}/route`);
    const candidateId = routePage.text.match(/name="candidateId" value="([^"]+)"/)![1];
    await router
      .post(`/bookings/${bookingId}/route`)
      .type('form')
      .send({ candidateId, arrivalDeadline: '2026-09-30', cargoType: 'GENERAL' });
    await sales.post(`/bookings/${bookingId}/confirm`);
    await router.post(`/bookings/${bookingId}/tracking-number`);
    const cargo = await ctx.db.selectFrom('cargo').select('trackingNumber').executeTakeFirstOrThrow();
    return { bookingId, trackingNumber: cargo.trackingNumber! };
  }
});
