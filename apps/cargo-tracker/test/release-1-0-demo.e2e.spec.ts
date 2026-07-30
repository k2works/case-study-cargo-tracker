import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { TestAgent, TestApp } from './test-app.js';
import { createTestApp, loginAsTestUser, waitUntil } from './test-app.js';
import { Role } from '../src/shared/domain/model/role.js';

/**
 * Release 1.0 デモ E2E（全業務フロー通し）。
 * 予約 → 経路 → 確定 → 追跡番号 → 荷役（受領・積込 → IN_TRANSIT・通関 → 引取 → DELIVERED）→
 * 料金算出（法人割引・例外調整）→ 精算書発行（INVOICE_ISSUED 通知）→ 入金確認 →
 * invoice CONFIRMED + cargo SETTLED、の一気通貫を 1 本で検証する。
 * 期限超過（OVERDUE + PAYMENT_OVERDUE 通知）は別 it で検証する。
 */
describe('Release 1.0 デモ（全業務フロー）', () => {
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
    await seedShipper('SHP-corporate', 'CORPORATE', 0.2);
    await seedShipper('SHP-individual', 'INDIVIDUAL', 0);
  });

  afterEach(async () => {
    await ctx.app.close();
  });

  it('予約から精算完了までの全業務フローが一気通貫で動作する（Release 1.0）', async () => {
    // 予約 → 経路 → 確定 → 追跡番号 → 荷役で DELIVERED まで
    const { bookingId, trackingNumber } = await deliverCargo('SHP-corporate');
    expect(trackingNumber).toBeTruthy();

    // 料金算出（法人割引 20% + 例外調整）→ 精算書発行
    const issued = await billing
      .post('/billing/invoices')
      .type('form')
      .send({
        bookingId,
        'adjustmentDescription[]': ['遅延補償'],
        'adjustmentAmount[]': ['10000'],
        'adjustmentDeduction[]': ['false'],
      });
    expect(issued.status).toBe(302);

    const invoice = await ctx.db
      .selectFrom('invoice')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    expect(invoice.paymentStatus).toBe('PENDING');
    // 法人割引 20% が適用されている（基本 1,680,000 × 20% = 336,000）
    expect(invoice.discountAmountValue).toBe(336_000);

    // 精算書発行の荷主通知（INVOICE_ISSUED）が本文付きで記録される
    const issuedNotice = await ctx.db
      .selectFrom('notification_record')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .where('notificationType', '=', 'INVOICE_ISSUED')
      .executeTakeFirstOrThrow();
    expect(issuedNotice.recipient).toBe('SHP-corporate@example.com');
    expect(issuedNotice.body).toContain(invoice.invoiceNumber);
    expect(issuedNotice.body).toContain('支払期限');

    // 請求書詳細で明細・支払状態を確認できる
    const detail = await billing.get(`/billing/invoices/${invoice.invoiceNumber}`);
    expect(detail.status).toBe(200);
    expect(detail.text).toContain('請求書詳細');
    expect(detail.text).toContain('法人割引');
    expect(detail.text).toContain('遅延補償');

    // 入金確認 → invoice CONFIRMED
    const confirmed = await billing.post(`/billing/invoices/${invoice.invoiceNumber}/confirm`);
    expect(confirmed.status).toBe(302);

    const afterConfirm = await ctx.db
      .selectFrom('invoice')
      .select('paymentStatus')
      .where('invoiceNumber', '=', invoice.invoiceNumber)
      .executeTakeFirstOrThrow();
    expect(afterConfirm.paymentStatus).toBe('CONFIRMED');

    // 入金記録（決済手段・取引参照）が保存される
    const payment = await ctx.db
      .selectFrom('payment')
      .innerJoin('invoice', 'invoice.id', 'payment.invoiceId')
      .select(['payment.paymentMethod as method', 'payment.transactionReference as ref'])
      .where('invoice.invoiceNumber', '=', invoice.invoiceNumber)
      .executeTakeFirstOrThrow();
    expect(payment.method).toBe('BANK_TRANSFER');
    expect(payment.ref).toContain('TXN-');

    // 精算完了イベント購読で cargo SETTLED（fire-and-forget のため待機）
    await waitUntil(async () =>
      (await ctx.db.selectFrom('cargo').select('bookingStatus').where('bookingId', '=', bookingId).executeTakeFirstOrThrow())
        .bookingStatus === 'SETTLED');
  });

  it('入金確認の重複配信でも cargo は SETTLED のまま不変（購読リスナーの冪等性）', async () => {
    const { bookingId } = await deliverCargo('SHP-individual');
    const issued = await billing.post('/billing/invoices').type('form').send({ bookingId });
    expect(issued.status).toBe(302);
    const invoice = await ctx.db
      .selectFrom('invoice')
      .select('invoiceNumber')
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();

    await billing.post(`/billing/invoices/${invoice.invoiceNumber}/confirm`);
    await waitUntil(async () =>
      (await ctx.db.selectFrom('cargo').select('bookingStatus').where('bookingId', '=', bookingId).executeTakeFirstOrThrow())
        .bookingStatus === 'SETTLED');

    // 同一予約への再イベント（別の精算完了配信を模擬）。既に SETTLED なので不変
    await billing.post(`/billing/invoices/${invoice.invoiceNumber}/confirm`); // 既に CONFIRMED → 200 表示
    await new Promise((resolve) => setTimeout(resolve, 100));
    const cargo = await ctx.db
      .selectFrom('cargo')
      .select('bookingStatus')
      .where('bookingId', '=', bookingId)
      .executeTakeFirstOrThrow();
    expect(cargo.bookingStatus).toBe('SETTLED');
  });

  it('支払期限を超過した請求は OVERDUE となり、経理担当者へ未払い通知が初回のみ記録される（US23-5）', async () => {
    const { bookingId } = await deliverCargo('SHP-individual');
    // 期限切れの請求書を直接投入（testClock 2027-06-01 より前の期限）
    await ctx.db
      .insertInto('invoice')
      .values({
        invoiceNumber: 'INV-OVERDUE1',
        bookingId,
        shipperId: 'SHP-individual',
        shipperType: 'INDIVIDUAL',
        baseAmountValue: 100_000,
        baseAmountCurrency: 'JPY',
        totalAmountValue: 110_000,
        totalAmountCurrency: 'JPY',
        taxRate: '0.10',
        taxAmount: '10000',
        paymentStatus: 'PENDING',
        issuedAt: new Date('2027-01-01T00:00:00Z'),
        dueDate: '2027-01-31',
        discountAmountValue: 0,
        discountAmountCurrency: 'JPY',
      })
      .execute();

    // 一覧照会で期限超過判定が走る
    const list = await billing.get('/billing/invoices');
    expect(list.status).toBe(200);

    const invoice = await ctx.db
      .selectFrom('invoice')
      .select('paymentStatus')
      .where('invoiceNumber', '=', 'INV-OVERDUE1')
      .executeTakeFirstOrThrow();
    expect(invoice.paymentStatus).toBe('OVERDUE');

    const overdueNotices = await ctx.db
      .selectFrom('notification_record')
      .selectAll()
      .where('notificationType', '=', 'PAYMENT_OVERDUE')
      .execute();
    expect(overdueNotices).toHaveLength(1);
    expect(overdueNotices[0].body).toContain('INV-OVERDUE1');

    // 再度照会しても未払い通知は増えない（初回遷移時のみ）
    await billing.get('/billing/invoices');
    const again = await ctx.db
      .selectFrom('notification_record')
      .select((eb) => eb.fn.countAll<string>().as('count'))
      .where('notificationType', '=', 'PAYMENT_OVERDUE')
      .executeTakeFirstOrThrow();
    expect(Number(again.count)).toBe(1);
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

  async function deliverCargo(shipperCode: string): Promise<{ bookingId: string; trackingNumber: string }> {
    const { bookingId, trackingNumber } = await issueTracking(shipperCode);
    await registerHandling({ trackingNumber, eventType: 'RECEIVE', location: 'JPTYO', completionTime: '2026-09-01T08:00' });
    await registerHandling({ trackingNumber, eventType: 'LOAD', location: 'JPTYO', completionTime: '2026-09-01T10:00', voyageNumber: 'V001' });
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
