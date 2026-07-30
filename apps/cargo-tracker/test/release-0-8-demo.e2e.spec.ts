import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser, waitUntil } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * Release 0.8 デモ E2E（IT6 タスク 4.3）。
 * 予約 → 経路 → 確定 → 追跡番号発行 → 荷役（受領・積込）→ 公開ページ照会 →
 * 遅延例外 → 紛失エスカレーション → 対応報告 → 解決（状態復帰）→
 * 通関 HELD → CUSTOMS_HOLD 自動登録、までを 1 本の業務シナリオで通しで検証する。
 */
describe('Release 0.8 デモシナリオ (IT5-IT6 統合)', () => {
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

  it('予約から通関留置まで貨物ライフサイクルを通しで完了できる', async () => {
    // 1) 予約 → 経路 → 確定 → 追跡番号発行
    const { trackingNumber } = await issueTracking();
    let tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('NOT_RECEIVED');

    // 2) 荷役（受領・積込）で貨物状態が自動更新される
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('RECEIVED');
    await registerHandling({ trackingNumber, eventType: 'LOAD', location: 'JPTYO', completionTime: '2026-09-01T10:00', voyageNumber: 'V001' });
    tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('LOADED');

    // 3) 公開ページで照会できる（ログイン不要）
    const publicPage = await ctx.db.selectFrom('cargo').select('trackingNumber').executeTakeFirstOrThrow();
    expect(publicPage.trackingNumber).toBe(trackingNumber);

    // 4) 遅延例外を登録 → EXCEPTION
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DELAY', location: 'USLAX', occurredAt: '2026-09-05T10:00', description: '悪天候' });
    tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('EXCEPTION');
    const delay = await ctx.db
      .selectFrom('tracking_exception_event')
      .selectAll()
      .where('exceptionType', '=', 'DELAY')
      .executeTakeFirstOrThrow();

    // 5) 紛失例外はエスカレーションされる
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'LOST', location: 'USLAX', occurredAt: '2026-09-06T10:00', description: 'コンテナ紛失' });
    const lost = await ctx.db
      .selectFrom('tracking_exception_event')
      .selectAll()
      .where('exceptionType', '=', 'LOST')
      .executeTakeFirstOrThrow();
    expect(lost.escalationFlag).toBe(true);

    // 6) 対応報告 → 解決（複数例外が残るため EXCEPTION は維持）
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions/${delay.id}/report`)
      .type('form')
      .send({ newEstimatedArrival: '2026-10-05T00:00', notes: '代替便を手配' });
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions/${delay.id}/resolve`)
      .type('form')
      .send({ resolutionNotes: '遅延解消' });
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions/${lost.id}/resolve`)
      .type('form')
      .send({ resolutionNotes: '発見・回収' });
    tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    // 全例外解決で発生前状態（LOADED）へ復帰する
    expect(tracking.transportStatus).toBe('LOADED');

    // 7) 通関申告 → HELD で CUSTOMS_HOLD 自動登録・EXCEPTION 復帰
    await handler.post(`/tracking/${trackingNumber}/customs`).type('form').send({});
    const declaration = await ctx.db.selectFrom('customs_declaration').selectAll().executeTakeFirstOrThrow();
    await handler
      .post(`/tracking/${trackingNumber}/customs/${declaration.declarationNumber}/status`)
      .type('form')
      .send({ status: 'HELD' });
    await waitForEvents();
    const customsHolds = (await ctx.db.selectFrom('tracking_exception_event').selectAll().execute()).filter(
      (e) => e.exceptionType === 'CUSTOMS_HOLD',
    );
    expect(customsHolds).toHaveLength(1);
    tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('EXCEPTION');
  });

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
