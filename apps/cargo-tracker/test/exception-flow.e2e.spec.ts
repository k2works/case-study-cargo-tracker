import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * IT6 例外処理フロー（US19 遅延・US20 破損/紛失）。
 * 例外登録 → EXCEPTION 表示 → 荷主通知 → 対応報告 → 解決で発生前状態へ復帰までを、
 * 対象ロール（追跡管理者・荷役作業員）が画面操作で完結できることを検証する。
 * LOST のエスカレーション・ロール別種別制御・冪等性・複数例外併存も確認する。
 */
describe('例外処理フロー (US19/US20)', () => {
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

  it('遅延例外を登録すると EXCEPTION になり荷主へ通知され、対応報告→解決で発生前状態へ復帰する（US19）', async () => {
    const { trackingNumber } = await issueTracking();
    // 発生前状態を RECEIVED にする
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    // 追跡管理者は詳細から例外登録へ到達できる
    const detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain('例外を登録');
    const form = await tracker.get(`/tracking/${trackingNumber}/exceptions/new`);
    expect(form.status).toBe(200);
    expect(form.text).toContain('例外登録');

    // 遅延を登録
    const register = await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DELAY', location: 'USLAX', occurredAt: '2026-09-05T10:00', description: '悪天候による遅延' });
    expect(register.status).toBe(302);

    // 貨物状態が EXCEPTION（例外発生）になる
    const activity = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(activity.transportStatus).toBe('EXCEPTION');

    // 荷主へ例外発生通知が記録される
    // 注: register() が notifier を await する同期実装である前提で登録直後にアサートしている。
    // fire-and-forget 化する場合は customs-flow の waitUntil パターンへ切り替えること。
    let notices = await ctx.db.selectFrom('notification_record').selectAll().execute();
    expect(notices.some((n) => n.notificationType === 'EXCEPTION_REPORTED' && n.recipient === 's@example.com')).toBe(true);

    // 例外一覧に「対応中」バッジ付きで表示される
    const list = await tracker.get(`/tracking/${trackingNumber}/exceptions`);
    expect(list.text).toContain('遅延');
    expect(list.text).toContain('対応中');

    const exceptionId = (await ctx.db.selectFrom('tracking_exception_event').select('id').executeTakeFirstOrThrow()).id;

    // 対応報告を送信（新到着予定日・対応方針）
    const report = await tracker
      .post(`/tracking/${trackingNumber}/exceptions/${exceptionId}/report`)
      .type('form')
      .send({ newEstimatedArrival: '2026-10-05T00:00', notes: '代替便を手配しました' });
    expect(report.status).toBe(302);
    notices = await ctx.db.selectFrom('notification_record').selectAll().execute();
    expect(notices.some((n) => n.notificationType === 'EXCEPTION_REPORT')).toBe(true);
    const afterReport = await tracker.get(`/tracking/${trackingNumber}/exceptions`);
    expect(afterReport.text).toContain('報告済');

    // 解決すると発生前状態（RECEIVED）へ復帰する
    const resolve = await tracker
      .post(`/tracking/${trackingNumber}/exceptions/${exceptionId}/resolve`)
      .type('form')
      .send({ resolutionNotes: '遅延解消' });
    expect(resolve.status).toBe(302);
    const resolved = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(resolved.transportStatus).toBe('RECEIVED');
  });

  it('紛失例外は緊急フラグが立ち管理職へエスカレーション通知される（US20）', async () => {
    const { trackingNumber } = await issueTracking();
    const register = await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'LOST', location: 'USLAX', occurredAt: '2026-09-05T10:00', description: 'コンテナ紛失' });
    expect(register.status).toBe(302);

    const exception = await ctx.db.selectFrom('tracking_exception_event').selectAll().executeTakeFirstOrThrow();
    expect(exception.escalationFlag).toBe(true);

    const notices = await ctx.db.selectFrom('notification_record').selectAll().execute();
    expect(notices.some((n) => n.notificationType === 'ESCALATION')).toBe(true);
    expect(notices.some((n) => n.notificationType === 'EXCEPTION_REPORTED')).toBe(true);
  });

  it('荷役作業員は破損・紛失のみ登録でき、荷役一覧から例外登録へ到達できる（US20・4.2）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    // 荷役一覧に例外登録導線が表示される
    const handlingList = await handler.get('/handling');
    expect(handlingList.text).toContain('例外を登録');

    // 荷役作業員は登録フォームに到達できる
    const form = await handler.get(`/tracking/${trackingNumber}/exceptions/new`);
    expect(form.status).toBe(200);

    // 破損は登録できる
    const damage = await handler
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DAMAGE', location: 'JPTYO', occurredAt: '2026-09-05T10:00', description: '外装破損' });
    expect(damage.status).toBe(302);
    const activity = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(activity.transportStatus).toBe('EXCEPTION');

    // 遅延はドメイン/認可エラーで拒否される（種別レベルの制御）
    const delay = await handler
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: '2026-09-05T11:00', description: 'x' });
    expect(delay.status).toBe(302);
    const back = await handler.get(`/tracking/${trackingNumber}/exceptions/new`);
    expect(back.text).toContain('登録できません');
    const events = await ctx.db.selectFrom('tracking_exception_event').selectAll().execute();
    expect(events.filter((e) => e.exceptionType === 'DELAY')).toHaveLength(0);
  });

  it('破損例外はエスカレーションされない（緊急フラグ false・ESCALATION 通知 0 件）（US20 負の検証）', async () => {
    const { trackingNumber } = await issueTracking();
    const register = await handler
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DAMAGE', location: 'JPTYO', occurredAt: '2026-09-05T10:00', description: '外装破損' });
    expect(register.status).toBe(302);

    const exception = await ctx.db.selectFrom('tracking_exception_event').selectAll().executeTakeFirstOrThrow();
    expect(exception.escalationFlag).toBe(false);

    const notices = await ctx.db.selectFrom('notification_record').selectAll().execute();
    expect(notices.filter((n) => n.notificationType === 'ESCALATION')).toHaveLength(0);
    // 例外発生通知自体は荷主へ送られる
    expect(notices.some((n) => n.notificationType === 'EXCEPTION_REPORTED')).toBe(true);
  });

  it('数値でない例外 ID の対応報告・解決は「不正な例外 ID です」で一覧へ戻される', async () => {
    const { trackingNumber } = await issueTracking();
    const report = await tracker.post(`/tracking/${trackingNumber}/exceptions/abc/report`).type('form').send({ notes: 'x' });
    expect(report.status).toBe(302);
    expect(report.headers.location).toBe(`/tracking/${trackingNumber}/exceptions`);
    const list = await tracker.get(`/tracking/${trackingNumber}/exceptions`);
    expect(list.text).toContain('不正な例外 ID です');
  });

  it('同種の未解決例外の重複登録はスキップされる（冪等）', async () => {
    const { trackingNumber } = await issueTracking();
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DELAY', location: 'USLAX', occurredAt: '2026-09-05T10:00', description: '1回目' });
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DELAY', location: 'USLAX', occurredAt: '2026-09-06T10:00', description: '2回目' });
    const events = await ctx.db.selectFrom('tracking_exception_event').selectAll().execute();
    expect(events.filter((e) => e.exceptionType === 'DELAY')).toHaveLength(1);
  });

  it('複数例外が併存する場合、全て解決するまで EXCEPTION を維持する', async () => {
    const { trackingNumber } = await issueTracking();
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DELAY', location: 'USLAX', occurredAt: '2026-09-05T10:00', description: null });
    await tracker
      .post(`/tracking/${trackingNumber}/exceptions`)
      .type('form')
      .send({ exceptionType: 'DAMAGE', location: 'USLAX', occurredAt: '2026-09-05T11:00', description: null });
    const events = await ctx.db.selectFrom('tracking_exception_event').selectAll().orderBy('id').execute();
    expect(events).toHaveLength(2);

    // 1件目を解決してもまだ EXCEPTION
    await tracker.post(`/tracking/${trackingNumber}/exceptions/${events[0].id}/resolve`).type('form').send({});
    let activity = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(activity.transportStatus).toBe('EXCEPTION');

    // 2件目も解決すると発生前状態（NOT_RECEIVED）へ復帰
    await tracker.post(`/tracking/${trackingNumber}/exceptions/${events[1].id}/resolve`).type('form').send({});
    activity = await ctx.db.selectFrom('tracking_activity').selectAll().executeTakeFirstOrThrow();
    expect(activity.transportStatus).toBe('NOT_RECEIVED');
  });

  it('ロール制御: 営業は例外登録できず、荷役作業員は例外一覧・解決に到達できない', async () => {
    const { trackingNumber } = await issueTracking();
    const salesPost = await sales.post(`/tracking/${trackingNumber}/exceptions`).type('form').send({});
    expect(salesPost.status).toBe(403);
    const handlerList = await handler.get(`/tracking/${trackingNumber}/exceptions`);
    expect(handlerList.status).toBe(403);
    const handlerResolve = await handler.post(`/tracking/${trackingNumber}/exceptions/1/resolve`).type('form').send({});
    expect(handlerResolve.status).toBe(403);
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
