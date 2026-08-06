import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser, waitUntil } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * IT7 料金算出・法人割引フロー（US21/US22・グループ 3）。
 * 引取済（DELIVERED）の予約に対して、経理担当者が請求書一覧 → 料金算出（実績・基本料金・割引根拠・
 * 例外調整）→ 発行（PENDING）までを画面操作で完結できることを検証する。
 * DELIVERED への遷移は荷役イベント購読（LOAD → IN_TRANSIT、CLAIM → DELIVERED）で自動的に進む。
 */
describe('料金算出・法人割引フロー (US21/US22)', () => {
  let ctx: TestApp;
  let sales: TestAgent;
  let router: TestAgent;
  let handler: TestAgent;
  let billing: TestAgent;

  beforeEach(async () => {
    ctx = await createTestApp();
    await seedLocations();
    sales = await loginAsTestUser(ctx, { username: 'sales1', roles: [Role.SALES] });
    router = await loginAsTestUser(ctx, { username: 'router1', roles: [Role.ROUTE_DESIGNER] });
    handler = await loginAsTestUser(ctx, { username: 'handler1', roles: [Role.HANDLER] });
    billing = await loginAsTestUser(ctx, { username: 'billing1', roles: [Role.BILLING] });
    await seedVoyage();
    await seedShipper('SHP-individual', 'INDIVIDUAL', 0);
    await seedShipper('SHP-corporate', 'CORPORATE', 0.2);
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('引取済の予約は請求書一覧に未請求として表示され、料金算出画面で実績と基本料金を確認できる（US21）', async () => {
    const { bookingId } = await deliverCargo('SHP-individual');

    // 請求書一覧に「未請求」の予約が出る（ナビ → 一覧到達）
    const list = await billing.get('/billing/invoices');
    expect(list.status).toBe(200);
    expect(list.text).toContain('請求管理');
    expect(list.text).toContain('未請求');
    expect(list.text).toContain(bookingId);

    // 料金算出画面に輸送実績（経路・重量・種別）と基本料金が表示される
    const form = await billing.get(`/billing/invoices/new?bookingId=${bookingId}`);
    expect(form.status).toBe(200);
    expect(form.text).toContain('料金算出');
    expect(form.text).toContain('JPTYO');
    expect(form.text).toContain('USLAX');
    expect(form.text).toContain('1200'); // 重量 kg
    expect(form.text).toContain('基本料金');
  });

  it('個人荷主は割引なしで発行され PENDING で登録される（US21/US22-3）', async () => {
    const { bookingId } = await deliverCargo('SHP-individual');

    const issued = await billing.post('/billing/invoices').type('form').send({ bookingId });
    expect(issued.status).toBe(302);

    const invoice = await ctx.db
      .selectFrom('invoice')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    expect(invoice.paymentStatus).toBe('PENDING');
    // 割引なし（個人）
    expect(invoice.discountAmountValue).toBe(0);
    // 基本料金 = 100 × 14日 × 1200kg × 1.0 = 1,680,000
    expect(invoice.baseAmountValue).toBe(1_680_000);

    // 一覧に発行済み（未払い）として表示される
    const list = await billing.get('/billing/invoices');
    expect(list.text).toContain(invoice.invoiceNumber);
    expect(list.text).toContain('未払い');
  });

  it('法人荷主は契約割引率が自動適用され、割引根拠が明細に記載される（US22）', async () => {
    const { bookingId } = await deliverCargo('SHP-corporate');

    // 料金算出画面に割引根拠（割引率）が表示される
    const form = await billing.get(`/billing/invoices/new?bookingId=${bookingId}`);
    expect(form.text).toContain('割引');
    expect(form.text).toContain('20'); // 割引率 20%

    const issued = await billing.post('/billing/invoices').type('form').send({ bookingId });
    expect(issued.status).toBe(302);

    const invoice = await ctx.db
      .selectFrom('invoice')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    // 割引額 = 1,680,000 × 20% = 336,000
    expect(invoice.discountAmountValue).toBe(336_000);

    const items = await ctx.db
      .selectFrom('invoice_line_item')
      .selectAll()
      .where('invoiceId', '=', invoice.id)
      .execute();
    expect(items.some((i) => i.description.includes('法人割引'))).toBe(true);
  });

  it('例外発生時に料金調整（減額・補償費用）を入力でき、明細に記録される（US21-6）', async () => {
    const { bookingId } = await deliverCargo('SHP-individual');

    const issued = await billing
      .post('/billing/invoices')
      .type('form')
      .send({
        bookingId,
        'adjustmentDescription[]': ['遅延補償', '破損減額'],
        'adjustmentAmount[]': ['5000', '3000'],
        'adjustmentDeduction[]': ['false', 'true'],
      });
    expect(issued.status).toBe(302);

    const invoice = await ctx.db
      .selectFrom('invoice')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    const items = await ctx.db
      .selectFrom('invoice_line_item')
      .selectAll()
      .where('invoiceId', '=', invoice.id)
      .execute();
    expect(items.some((i) => i.description.includes('遅延補償'))).toBe(true);
    expect(items.some((i) => i.description.includes('破損減額'))).toBe(true);
    // 補償 +5000、減額 -3000 → 基本 1,680,000 + 5000 - 3000 = 1,682,000（割引なし・税 10%）
    expect(invoice.totalAmountValue).toBe(Math.round(1_682_000 * 1.1));
  });

  it('未解決の例外があると料金算出画面に発行前の注意喚起を表示する（US21-6・user#5）', async () => {
    const { bookingId } = await deliverCargo('SHP-individual');
    // 未解決（resolvedAt=null）の追跡例外を投入する
    const tracking = await ctx.db
      .selectFrom('tracking_activity')
      .select('id')
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    await ctx.db
      .insertInto('tracking_exception_event')
      .values({
        trackingId: tracking.id,
        exceptionType: 'DELAY',
        occurredAt: new Date('2026-09-10T00:00:00Z'),
        locationUnlocode: 'USLAX',
        description: '悪天候による遅延',
        statusBeforeException: 'IN_TRANSIT',
      })
      .execute();

    const form = await billing.get(`/billing/invoices/new?bookingId=${bookingId}`);
    expect(form.status).toBe(200);
    expect(form.text).toContain('未解決の例外があります');
    expect(form.text).toContain('料金調整の要否');
  });

  it('引取済でない予約は請求できない（US21-1）', async () => {
    const { bookingId } = await issueTracking('SHP-individual');
    const res = await billing.post('/billing/invoices').type('form').send({ bookingId });
    expect(res.status).toBe(200);
    expect(res.text).toContain('引取済');
    const invoice = await ctx.db.selectFrom('invoice').selectAll().where('bookingId', '=', bookingId).executeTakeFirst();
    expect(invoice).toBeUndefined();
  });

  it('LOAD/引取イベントの重複配信でも貨物状態は不変（購読リスナーの冪等性）', async () => {
    const { bookingId, trackingNumber } = await deliverCargo('SHP-individual');
    // 既に DELIVERED。追加の LOAD（別日時）を登録しても不正遷移せず DELIVERED のまま
    await registerHandling({ trackingNumber, eventType: 'LOAD', location: 'JPTYO', completionTime: '2026-09-02T10:00', voyageNumber: 'V001' });
    // 反映猶予を与えても状態は変わらない
    await new Promise((resolve) => setTimeout(resolve, 100));
    const cargo = await ctx.db
      .selectFrom('cargo')
      .select('bookingStatus')
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    expect(cargo.bookingStatus).toBe('DELIVERED');
  });

  it('二重請求は拒否される', async () => {
    const { bookingId } = await deliverCargo('SHP-individual');
    const first = await billing.post('/billing/invoices').type('form').send({ bookingId });
    expect(first.status).toBe(302);
    const second = await billing.post('/billing/invoices').type('form').send({ bookingId });
    expect(second.status).toBe(200);
    expect(second.text).toContain('既に請求');
    const invoices = await ctx.db.selectFrom('invoice').selectAll().where('bookingId', '=', bookingId).execute();
    expect(invoices).toHaveLength(1);
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

  async function seedVoyage(): Promise<void> {
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

  async function seedShipper(code: string, shipperType: string, discountRate: number): Promise<void> {
    await ctx.db
      .insertInto('shipper')
      .values({
        shipperCode: code,
        shipperType,
        name: `荷主-${code}`,
        email: `${code}@example.com`,
        discountRate,
      })
      .execute();
  }

  /** 予約 → 経路 → 確定 → 追跡番号発行 まで実行し bookingId / trackingNumber を返す */
  async function issueTracking(shipperCode: string): Promise<{ bookingId: string; trackingNumber: string }> {
    const create = await sales
      .post('/bookings')
      .type('form')
      .send({
        shipperCode,
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
    const cargo = await ctx.db
      .selectFrom('cargo')
      .select(['bookingId', 'trackingNumber'])
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    return { bookingId, trackingNumber: cargo.trackingNumber! };
  }

  /** 追跡番号発行後、受領 → 積込 → 通関 CLEARED → 引取 まで進め DELIVERED にする */
  async function deliverCargo(shipperCode: string): Promise<{ bookingId: string; trackingNumber: string }> {
    const { bookingId, trackingNumber } = await issueTracking(shipperCode);
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    await registerHandling({ trackingNumber, eventType: 'LOAD', location: 'JPTYO', completionTime: '2026-09-01T10:00', voyageNumber: 'V001' });
    // LOAD 購読で IN_TRANSIT へ
    await waitUntil(async () =>
      (await ctx.db.selectFrom('cargo').select('bookingStatus').where('bookingId', '=', bookingId).executeTakeFirstOrThrow())
        .bookingStatus === 'IN_TRANSIT');

    await seedClearedCustoms(bookingId);
    await registerHandling({
      trackingNumber,
      eventType: 'CLAIM',
      location: 'USLAX',
      completionTime: '2026-09-16T10:00',
      consigneeConfirmation: 'CODE-123',
    });
    // CargoClaimedEvent 購読で DELIVERED へ
    await waitUntil(async () =>
      (await ctx.db.selectFrom('cargo').select('bookingStatus').where('bookingId', '=', bookingId).executeTakeFirstOrThrow())
        .bookingStatus === 'DELIVERED');
    return { bookingId, trackingNumber };
  }

  async function seedClearedCustoms(bookingId: string): Promise<void> {
    const activity = await ctx.db
      .selectFrom('handling_activity')
      .select('id')
      .where('bookingId', '=', bookingId)
      .where('eventType', '=', 'RECEIVE')
      .executeTakeFirstOrThrow();
    await ctx.db
      .insertInto('customs_declaration')
      .values({
        handlingActivityId: activity.id,
        declarationNumber: `DECL-${bookingId.slice(0, 8)}`,
        declaredAt: new Date('2026-09-10T00:00:00Z'),
        status: 'CLEARED',
        clearedAt: new Date('2026-09-12T00:00:00Z'),
      })
      .execute();
  }

  async function registerHandling(fields: Record<string, string>) {
    return handler.post('/handling').type('form').send(fields);
  }
});
