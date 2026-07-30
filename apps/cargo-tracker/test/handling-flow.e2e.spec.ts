import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * IT5 荷役・追跡フロー（US15/US16/US17）。
 * 追跡番号発行 → 荷役登録（受領・積込・荷降し）→ 貨物状態自動更新 → 引取（通関 CLEARED 前提）→
 * 手動更新を、対象ロール（荷役作業員・追跡管理者）が画面操作で完結できることを検証する。
 * イベント購読の冪等性・MISROUTED 反映・荷主通知記録も確認する（ADR-005/009）。
 */
describe('荷役・追跡フロー (US15-US17)', () => {
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

  it('追跡番号発行で NOT_RECEIVED の追跡レコードが作成される（Try T6）', async () => {
    const { trackingNumber } = await issueTracking();
    const activity = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(activity.trackingNumber).toBe(trackingNumber);
    expect(activity.transportStatus).toBe('NOT_RECEIVED');

    // 追跡詳細画面で受領待ちが表示される
    const page = await tracker.get(`/tracking/${trackingNumber}`);
    expect(page.status).toBe(200);
    expect(page.text).toContain('受領待ち');
  });

  it('荷役作業員が受領・積込を登録すると貨物状態が自動更新され荷主に通知される（US15）', async () => {
    const { trackingNumber } = await issueTracking();

    // ナビ → 一覧 → 新規登録フォームに到達できる（ロール完結導線・「荷役管理」メニュー表示）
    const list = await handler.get('/handling');
    expect(list.status).toBe(200);
    expect(list.text).toContain('荷役管理');
    expect(list.text).toContain('新規登録');
    const form = await handler.get('/handling/new');
    expect(form.status).toBe(200);
    expect(form.text).toContain('荷役作業登録');

    // 受領（RECEIVE）: 出発港一致
    const receive = await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    expect(receive.status).toBe(302);
    let tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('RECEIVED');

    // 積込（LOAD）: 積込港・航海一致
    await registerHandling({ trackingNumber, eventType: 'LOAD', location: 'JPTYO', completionTime: '2026-09-01T10:00', voyageNumber: 'V001' });
    tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('LOADED');

    // 一覧に履歴が表示される
    const after = await handler.get('/handling');
    expect(after.text).toContain('受領');
    expect(after.text).toContain('積込');
    expect(after.text).toContain('V001');

    // 荷主への状態変更通知が記録される（宛先は荷主メール）
    const notices = await ctx.db.selectFrom('notification_record').selectAll().execute();
    const statusNotices = notices.filter((n) => n.notificationType === 'STATUS_CHANGED');
    expect(statusNotices.length).toBeGreaterThanOrEqual(2);
    expect(statusNotices.every((n) => n.recipient === 's@example.com')).toBe(true);

    // 経路状態は ROUTED のまま
    const cargo = await ctx.db.selectFrom('cargo').selectAll().executeTakeFirstOrThrow();
    expect(cargo.routingStatus).toBe('ROUTED');
  });

  it('積込場所が予定ルートと異なると MISROUTED が記録され警告が表示される（US15）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    const misload = await registerHandling({ trackingNumber, eventType: 'LOAD', location: 'HKHKG', completionTime: '2026-09-01T10:00', voyageNumber: 'V001' });
    expect(misload.status).toBe(302);

    const list = await handler.get('/handling');
    expect(list.text).toContain('MISROUTED');

    const cargo = await ctx.db.selectFrom('cargo').selectAll().executeTakeFirstOrThrow();
    expect(cargo.routingStatus).toBe('MISROUTED');
  });

  it('受領場所が出発港と異なる場合は警告が表示される（MISROUTED にはならない）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'HKHKG', completionTime: '2026-09-01T08:00' });
    const list = await handler.get('/handling');
    expect(list.text).toContain('作業場所が予定ルートと異なります');
    const cargo = await ctx.db.selectFrom('cargo').selectAll().executeTakeFirstOrThrow();
    expect(cargo.routingStatus).toBe('ROUTED');
  });

  it('同一種別・同一日時の重複登録は荷役・追跡・通知とも冪等（ADR-009）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    const before = (await ctx.db.selectFrom('notification_record').selectAll().execute()).length;
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    const events = await ctx.db.selectFrom('tracking_handling_event').selectAll().execute();
    expect(events.filter((e) => e.eventType === 'RECEIVE')).toHaveLength(1);
    const handlings = await ctx.db.selectFrom('handling_activity').selectAll().execute();
    expect(handlings).toHaveLength(1);
    const after = (await ctx.db.selectFrom('notification_record').selectAll().execute()).length;
    expect(after).toBe(before); // 通知も再発行されない
  });

  it('手動更新では引取（CLAIM）を記録できない（US16 の不変条件迂回防止）', async () => {
    const { trackingNumber } = await issueTracking();
    const res = await tracker
      .post(`/tracking/${trackingNumber}/events`)
      .type('form')
      .send({ eventType: 'CLAIM', location: 'USLAX', completionTime: '2026-09-16T10:00' });
    expect(res.status).toBe(302);
    const detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('手動更新で記録できないイベント種別');
    const tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('NOT_RECEIVED');
  });

  it('追跡管理者が出港・入港を手動記録すると輸送中・引取待ちになる（US17）', async () => {
    const { trackingNumber } = await issueTracking();
    await tracker
      .post(`/tracking/${trackingNumber}/events`)
      .type('form')
      .send({ eventType: 'DEPARTURE', location: 'JPTYO', completionTime: '2026-09-02T09:00', voyageNumber: 'V001' });
    let detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('輸送中');
    await tracker
      .post(`/tracking/${trackingNumber}/events`)
      .type('form')
      .send({ eventType: 'ARRIVAL', location: 'USLAX', completionTime: '2026-09-15T09:00' });
    detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('引取待ち');
  });

  it('引取は通関 CLEARED が前提。CLEARED 後に荷受人確認で引取済になる（US16）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    // 通関未クリアの引取は拒否される
    const blocked = await registerHandling({
      trackingNumber,
      eventType: 'CLAIM',
      location: 'USLAX',
      completionTime: '2026-09-16T10:00',
      consigneeConfirmation: 'CODE-123',
    });
    expect(blocked.status).toBe(200);
    expect(blocked.text).toContain('CLEARED になるまで引取はできません');

    // 荷受人確認なしも拒否される
    await seedClearedCustoms();
    const noConfirm = await registerHandling({ trackingNumber, eventType: 'CLAIM', location: 'USLAX', completionTime: '2026-09-16T10:00' });
    expect(noConfirm.status).toBe(200);
    expect(noConfirm.text).toContain('荷受人確認');

    // CLEARED + 荷受人確認で引取が記録され、貨物状態が引取済になる
    const claim = await registerHandling({
      trackingNumber,
      eventType: 'CLAIM',
      location: 'USLAX',
      completionTime: '2026-09-16T10:00',
      consigneeConfirmation: 'CODE-123',
    });
    expect(claim.status).toBe(302);
    const tracking = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(tracking.transportStatus).toBe('CLAIMED');
  });

  it('存在しない追跡番号ではエラーメッセージが表示される（US15）', async () => {
    await issueTracking();
    const res = await registerHandling({ trackingNumber: 'TRK-MISSING', eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    expect(res.status).toBe(200);
    expect(res.text).toContain('追跡番号が見つかりません');
  });

  it('追跡管理者が貨物状態を手動更新でき、履歴と荷主通知が記録される（US17）', async () => {
    const { trackingNumber } = await issueTracking();

    // ナビ → 追跡入力 → 追跡詳細に到達できる（ロール完結導線・「貨物追跡」メニュー表示）
    const input = await tracker.get('/tracking');
    expect(input.status).toBe(200);
    expect(input.text).toContain('貨物追跡');
    const lookup = await tracker.get(`/tracking?trackingNumber=${trackingNumber}`);
    expect(lookup.status).toBe(302);
    expect(lookup.headers.location).toBe(`/tracking/${trackingNumber}`);

    const update = await tracker
      .post(`/tracking/${trackingNumber}/events`)
      .type('form')
      .send({ eventType: 'UNLOAD', location: 'USLAX', completionTime: '2026-09-15T09:00', voyageNumber: 'V001' });
    expect(update.status).toBe(302);

    const detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('荷降し済');
    expect(detail.text).toContain('USLAX');

    const notices = await ctx.db.selectFrom('notification_record').selectAll().execute();
    expect(notices.some((n) => n.notificationType === 'STATUS_CHANGED')).toBe(true);
  });

  it('未登録の追跡番号の照会はエラー表示で追跡入力に留まる', async () => {
    const res = await tracker.get('/tracking?trackingNumber=TRK-UNKNOWN');
    expect(res.status).toBe(200);
    expect(res.text).toContain('追跡番号が見つかりません');
  });

  it('ロール制御: 営業は荷役登録できず、荷役作業員は手動更新できない', async () => {
    const { trackingNumber } = await issueTracking();
    const salesPost = await sales.post('/handling').type('form').send({});
    expect(salesPost.status).toBe(403);
    const handlerUpdate = await handler.post(`/tracking/${trackingNumber}/events`).type('form').send({});
    expect(handlerUpdate.status).toBe(403);
    const handlerTracking = await handler.get('/tracking');
    expect(handlerTracking.status).toBe(403);
    // 追跡管理者は荷役登録できず、営業は手動更新できない（ロール×操作の負テスト網羅）
    const trackerPost = await tracker.post('/handling').type('form').send({});
    expect(trackerPost.status).toBe(403);
    const salesUpdate = await sales.post(`/tracking/${trackingNumber}/events`).type('form').send({});
    expect(salesUpdate.status).toBe(403);
  });

  it('手動更新の不正な位置・日時はエラーとして拒否される', async () => {
    const { trackingNumber } = await issueTracking();
    const badLocation = await tracker
      .post(`/tracking/${trackingNumber}/events`)
      .type('form')
      .send({ eventType: 'DEPARTURE', location: 'bad', completionTime: '2026-09-02T09:00' });
    expect(badLocation.status).toBe(302);
    let detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('不正な UN/LOCODE');

    const badTime = await tracker
      .post(`/tracking/${trackingNumber}/events`)
      .type('form')
      .send({ eventType: 'DEPARTURE', location: 'JPTYO', completionTime: 'not-a-date' });
    expect(badTime.status).toBe(302);
    detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('完了日時が不正');
  });

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

  async function seedClearedCustoms(): Promise<void> {
    const activity = await ctx.db.selectFrom('handling_activity').select('id').executeTakeFirstOrThrow();
    await ctx.db
      .insertInto('customs_declaration')
      .values({
        handlingActivityId: activity.id,
        declarationNumber: 'DECL-001',
        declaredAt: new Date('2026-09-10T00:00:00Z'),
        status: 'CLEARED',
        clearedAt: new Date('2026-09-12T00:00:00Z'),
      })
      .execute();
  }

  async function registerHandling(fields: Record<string, string>) {
    return handler.post('/handling').type('form').send(fields);
  }

  /** 予約登録 → 引き渡し → 経路確定 → 予約確定 → 追跡番号発行までを実行し追跡番号を返す */
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
