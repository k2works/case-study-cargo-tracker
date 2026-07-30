import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * IT6 追跡照会（US18）。
 * 追跡番号で現在状態・位置（港湾名）・推定到着日・イベント履歴を照会できること、
 * 認証付き追跡詳細の htmx 30 秒ポーリング（終端状態 CLAIMED で停止）、
 * 認証不要の公開ページ（最小表示・個人情報非露出）をアウトサイドインで検証する。
 */
describe('追跡照会 (US18)', () => {
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

  it('認証付き追跡詳細で状態・現在地（港湾名）・推定到着日・イベント履歴を照会できる（受入基準 1-3）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    const detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.status).toBe(200);
    // 状態
    expect(detail.text).toContain('受領済');
    // 現在地（UN/LOCODE + 港湾名）
    expect(detail.text).toContain('JPTYO');
    expect(detail.text).toContain('Tokyo');
    // 推定到着日（旅程の最終 unload_time 由来・YYYY-MM-DD 頃）
    expect(detail.text).toContain('2026-09-15 頃');
    // イベント履歴（時系列）
    expect(detail.text).toContain('受領');
  });

  it('旅程未確定の場合は推定到着日が「未確定」と表示される', async () => {
    const { trackingNumber } = await issueTracking();
    // 旅程（leg）が存在しない状態を作り、推定到着日が導出できないことを再現する
    await ctx.db.deleteFrom('leg').execute();
    const detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.status).toBe(200);
    expect(detail.text).toContain('未確定');
  });

  it('未存在の追跡番号は認証詳細で 404 になる', async () => {
    const res = await tracker.get('/tracking/TRK-UNKNOWN00001');
    expect(res.status).toBe(404);
  });

  it('htmx ポーリングフラグメントは認証必須・非終端状態では 30 秒ポーリングを含む', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    // 認証必須（未認証はログインへリダイレクト）
    const anon = await request(ctx.app.getHttpServer()).get(`/tracking/${trackingNumber}/status`);
    expect(anon.status).toBe(302);
    expect(anon.headers.location).toBe('/login');

    const fragment = await tracker.get(`/tracking/${trackingNumber}/status`);
    expect(fragment.status).toBe(200);
    expect(fragment.text).toContain('every 30s');
    expect(fragment.text).toContain(`/tracking/${trackingNumber}/status`);
  });

  it('終端状態（CLAIMED）のフラグメントはポーリングを停止し「輸送は完了しました」を表示する', async () => {
    const { trackingNumber } = await issueTracking();
    await ctx.db
      .updateTable('tracking_activity')
      .set({ transportStatus: 'CLAIMED' })
      .where('trackingNumber', '=', trackingNumber)
      .execute();

    const fragment = await tracker.get(`/tracking/${trackingNumber}/status`);
    expect(fragment.status).toBe(200);
    expect(fragment.text).not.toContain('every 30s');
    expect(fragment.text).toContain('輸送は完了しました');
  });

  it('公開ページはログインなしで照会でき、状態・現在地・履歴を表示する（受入基準 5）', async () => {
    const { trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    const anon = request.agent(ctx.app.getHttpServer());
    const res = await anon.get(`/public/tracking/${trackingNumber}`);
    expect(res.status).toBe(200);
    expect(res.text).toContain('CargoTracker 公開追跡');
    expect(res.text).toContain('受領済');
    expect(res.text).toContain('Tokyo');
    expect(res.text).toContain('受領');
  });

  it('公開フォーム（GET /public/tracking）はログインなしで表示される', async () => {
    const anon = request.agent(ctx.app.getHttpServer());
    const res = await anon.get('/public/tracking');
    expect(res.status).toBe(200);
    expect(res.text).toContain('CargoTracker 公開追跡');
  });

  it('公開ページに予約 ID・荷主名・荷受人・メール等の照会範囲外情報は露出しない（受入基準・最小表示）', async () => {
    const { bookingId, trackingNumber } = await issueTracking();
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });

    const anon = request.agent(ctx.app.getHttpServer());
    const res = await anon.get(`/public/tracking/${trackingNumber}`);
    expect(res.status).toBe(200);
    expect(res.text).not.toContain(bookingId);
    expect(res.text).not.toContain('荷主');
    expect(res.text).not.toContain('受取花子');
    expect(res.text).not.toContain('uke@example.com');
    expect(res.text).not.toContain('s@example.com');
  });

  it('公開ページで未存在の追跡番号は「追跡番号が見つかりません」と表示される（受入基準 4）', async () => {
    const anon = request.agent(ctx.app.getHttpServer());
    const res = await anon.get('/public/tracking/TRK-UNKNOWN00001');
    expect(res.status).toBe(200);
    expect(res.text).toContain('追跡番号が見つかりません');
  });

  it('認証付き追跡詳細には公開ページ URL の案内が表示される（導線）', async () => {
    const { trackingNumber } = await issueTracking();
    const detail = await tracker.get(`/tracking/${trackingNumber}`);
    expect(detail.text).toContain(`/public/tracking/${trackingNumber}`);
  });

  async function seedLocations(): Promise<void> {
    await ctx.db
      .insertInto('location')
      .values([
        { unlocode: 'JPTYO', name: 'Tokyo' },
        { unlocode: 'USLAX', name: 'Los Angeles' },
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
