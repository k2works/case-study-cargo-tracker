import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * IT4 Release 0.5 基幹フロー（US09〜US14）。
 * 予約登録 → 引き渡し → 経路確定 → 通知 → 予約確定 → 追跡番号発行を
 * 対象ロールが画面操作で完結できることを検証する（IT3 Try T1）。
 */
describe('経路確定・予約確定・追跡番号発行フロー (US09-US14)', () => {
  let ctx: TestApp;
  let sales: TestAgent;
  let router: TestAgent;

  beforeEach(async () => {
    ctx = await createTestApp();
    await seedLocations();
    sales = await loginAsTestUser(ctx, { username: 'sales1', roles: [Role.SALES] });
    router = await loginAsTestUser(ctx, { username: 'router1', roles: [Role.ROUTE_DESIGNER] });
    await seedShipper();
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('直行便を確定し予約確定・追跡番号発行まで完結できる', async () => {
    await registerDirectVoyage();
    const bookingId = await registerAndAssign();

    // 経路設計者: 経路割り当て画面に到達し候補が表示される（US09）
    const routePage = await router.get(`/bookings/${bookingId}/route`);
    expect(routePage.status).toBe(200);
    expect(routePage.text).toContain('経路割り当て');
    expect(routePage.text).toContain('V001');
    expect(routePage.text).toContain('直行');

    // 経路を選択・紐付け（US09/US11）→ ROUTE_PROPOSED
    const candidateId = extractCandidateId(routePage.text);
    const assign = await router
      .post(`/bookings/${bookingId}/route`)
      .type('form')
      .send({ candidateId, arrivalDeadline: '2026-09-30', cargoType: 'GENERAL' });
    expect(assign.status).toBe(302);
    expect(assign.headers.location).toBe(`/bookings/${bookingId}`);

    const afterRoute = await router.get(`/bookings/${bookingId}`);
    expect(afterRoute.text).toContain('経路提案中');
    expect(afterRoute.text).toContain('確定経路');
    expect(afterRoute.text).toContain('V001');

    // 営業: 通知内容確認画面（US12 是正・IT4 Try T1）→ 経由港・所要日数・到着予定日・料金概算・荷主宛先を確認できる
    const notifyPage = await sales.get(`/bookings/${bookingId}/notify`);
    expect(notifyPage.status).toBe(200);
    expect(notifyPage.text).toContain('通知内容の確認');
    expect(notifyPage.text).toContain('s@example.com'); // 宛先は荷主（shipper）メール
    expect(notifyPage.text).toContain('経由港');
    expect(notifyPage.text).toContain('所要日数');
    expect(notifyPage.text).toContain('到着予定日');
    expect(notifyPage.text).toContain('料金概算');
    expect(notifyPage.text).toContain('2026-09-15'); // 最終区間の到着日

    // 営業: 荷主へ通知（US12）→ 荷主メール宛の通知記録が残る
    const notify = await sales.post(`/bookings/${bookingId}/notify`);
    expect(notify.status).toBe(302);
    const notified = await ctx.db.selectFrom('notification_record').selectAll().execute();
    const routeNotice = notified.find((n) => n.notificationType === 'ROUTE_PROPOSED');
    expect(routeNotice?.recipient).toBe('s@example.com');

    // 営業: 予約を確定（US13）→ CONFIRMED
    const confirm = await sales.post(`/bookings/${bookingId}/confirm`);
    expect(confirm.status).toBe(302);
    const afterConfirm = await sales.get(`/bookings/${bookingId}`);
    expect(afterConfirm.text).toContain('確定');

    // 経路設計者: 追跡番号を発行（US14）→ TRACKING_ISSUED
    const issue = await router.post(`/bookings/${bookingId}/tracking-number`);
    expect(issue.status).toBe(302);
    const afterIssue = await router.get(`/bookings/${bookingId}`);
    expect(afterIssue.text).toContain('追跡発行済');
    expect(afterIssue.text).toContain('TRK-');

    const saved = await ctx.db.selectFrom('cargo').selectAll().executeTakeFirstOrThrow();
    expect(saved.bookingStatus).toBe('TRACKING_ISSUED');
    expect(saved.trackingNumber).toMatch(/^TRK-/);
    const legs = await ctx.db.selectFrom('leg').selectAll().execute();
    expect(legs).toHaveLength(1);
    // US14: 荷主への追跡番号通知が記録される
    const trackingNotice = await ctx.db.selectFrom('notification_record').selectAll().execute();
    expect(trackingNotice.some((n) => n.notificationType === 'TRACKING_ISSUED')).toBe(true);
  });

  it('経路提案を差し戻すと経路設計中に戻る（US13）', async () => {
    await registerDirectVoyage();
    const bookingId = await registerAndAssign();
    const routePage = await router.get(`/bookings/${bookingId}/route`);
    const candidateId = extractCandidateId(routePage.text);
    await router.post(`/bookings/${bookingId}/route`).type('form').send({ candidateId, arrivalDeadline: '2026-09-30', cargoType: 'GENERAL' });

    const back = await sales.post(`/bookings/${bookingId}/return-to-routing`);
    expect(back.status).toBe(302);
    const detail = await sales.get(`/bookings/${bookingId}`);
    expect(detail.text).toContain('経路設計中');
  });

  it('予約をキャンセルするとキャンセル通知が記録される（US13）', async () => {
    await registerDirectVoyage();
    const bookingId = await registerAndAssign();
    const routePage = await router.get(`/bookings/${bookingId}/route`);
    const candidateId = extractCandidateId(routePage.text);
    await router.post(`/bookings/${bookingId}/route`).type('form').send({ candidateId, arrivalDeadline: '2026-09-30', cargoType: 'GENERAL' });

    const cancel = await sales.post(`/bookings/${bookingId}/cancel`);
    expect(cancel.status).toBe(302);
    const detail = await sales.get(`/bookings/${bookingId}`);
    expect(detail.text).toContain('キャンセル');
    const records = await ctx.db.selectFrom('notification_record').selectAll().execute();
    expect(records.some((n) => n.notificationType === 'BOOKING_CANCELLED')).toBe(true);
  });

  it('期限内候補がない場合は条件協議依頼が表示される（US10）', async () => {
    const bookingId = await registerAndAssign();
    const routePage = await router.get(`/bookings/${bookingId}/route`);
    expect(routePage.status).toBe(200);
    expect(routePage.text).toContain('営業担当者に条件協議を依頼');
  });

  it('営業担当者は経路割り当て画面に到達できない（ロール制御）', async () => {
    const bookingId = await registerAndAssign();
    const res = await sales.get(`/bookings/${bookingId}/route`);
    expect(res.status).toBe(403);
  });

  it('経路設計者は営業専用の確定・通知・キャンセル操作を実行できない（越権遮断）', async () => {
    const bookingId = await registerAndAssign();
    for (const path of ['confirm', 'notify', 'cancel', 'return-to-routing']) {
      const res = await router.post(`/bookings/${bookingId}/${path}`);
      expect(res.status).toBe(403);
    }
    const page = await router.get(`/bookings/${bookingId}/notify`);
    expect(page.status).toBe(403);
  });

  it('営業担当者は追跡番号発行を実行できない（US14 は経路設計者ロール）', async () => {
    const bookingId = await registerAndAssign();
    const res = await sales.post(`/bookings/${bookingId}/tracking-number`);
    expect(res.status).toBe(403);
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

  async function registerDirectVoyage(): Promise<void> {
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
  }

  async function registerAndAssign(): Promise<string> {
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
    return bookingId;
  }

  function extractCandidateId(html: string): string {
    const match = html.match(/name="candidateId" value="([^"]+)"/);
    if (match === null) {
      throw new Error('候補 ID が画面に見つかりません');
    }
    return match[1];
  }
});
