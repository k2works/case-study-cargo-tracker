import { randomUUID } from 'node:crypto';
import { Decimal } from 'decimal.js';
import { type Clock, systemClock } from '../../../../shared/infrastructure/clock/clock.js';
import type { InvoiceRepository } from '../../domain/repository/invoice-repository.js';
import type { BillingSnapshotAcl } from '../outboundservices/acl/billing-snapshot-acl.js';
import { FreightCalculator } from '../../domain/model/freight-calculator.js';
import { Invoice } from '../../domain/model/invoice.js';
import { BillingBookingId } from '../../domain/model/billing-booking-id.js';
import { BillingShipperId } from '../../domain/model/billing-shipper-id.js';
import { DiscountRate } from '../../domain/model/discount-rate.js';
import { InvoiceLineItem } from '../../domain/model/invoice-line-item.js';
import { Money } from '../../domain/model/money.js';
import { BillingValidationError } from '../../domain/model/billing-validation-error.js';

/** 請求書の通貨（国内輸送を前提に JPY 固定） */
const INVOICE_CURRENCY = 'JPY';

/** 料金調整の入力（例外発生時の減額・補償費用。US21-6） */
export interface InvoiceAdjustmentInput {
  description: string;
  amountValue: number;
  /** true なら控除（減額）、false なら加算（補償費用など） */
  isDeduction: boolean;
}

/**
 * 請求書発行ユースケース（US21/US22・グループ 3）。
 * 引取済（DELIVERED）の予約について輸送実績（ACL 取得）から基本料金を算出し、
 * 例外調整・法人割引を反映した請求書を PENDING で発行する。
 * 荷主への精算書通知・入金確認・SETTLED 反映はグループ 4 で扱う（ここでは発行のみ）。
 */
export class GenerateInvoiceService {
  private readonly calculator = new FreightCalculator();

  constructor(
    private readonly invoices: InvoiceRepository,
    private readonly snapshots: BillingSnapshotAcl,
    private readonly now: Clock = systemClock,
  ) {}

  async generate(bookingId: string, adjustments: InvoiceAdjustmentInput[] = []): Promise<string> {
    const snapshot = await this.snapshots.findByBookingId(bookingId);
    if (snapshot === null) {
      throw new BillingValidationError(`予約が見つかりません: ${bookingId}`);
    }
    // 引取（CLAIM）成功で Booking は DELIVERED へ遷移する。DELIVERED のみ請求可能
    if (snapshot.bookingStatus !== 'DELIVERED') {
      throw new BillingValidationError('引取済（DELIVERED）の予約のみ請求できます');
    }
    // 二重請求防止（サービス層の事前チェック。DB の booking_id UNIQUE と二層防御）
    const existing = await this.invoices.findByBookingId(bookingId);
    if (existing !== null) {
      throw new BillingValidationError('既に請求済みの予約です');
    }

    const baseAmount = this.calculator.calculate({
      transitDays: snapshot.transitDays,
      weightKg: new Decimal(snapshot.weightKg),
      cargoType: snapshot.cargoType,
    });

    const additions: InvoiceLineItem[] = [];
    const deductions: InvoiceLineItem[] = [];
    for (const adjustment of adjustments) {
      const item = InvoiceLineItem.of(
        adjustment.description,
        Money.of(adjustment.amountValue, INVOICE_CURRENCY),
      );
      (adjustment.isDeduction ? deductions : additions).push(item);
    }

    const invoice = Invoice.issue({
      invoiceNumber: `INV-${randomUUID().slice(0, 8).toUpperCase()}`,
      cargoBookingId: BillingBookingId.of(snapshot.bookingId),
      shipperId: BillingShipperId.of(snapshot.shipperId, snapshot.shipperType),
      baseAmount,
      adjustments: additions,
      deductions,
      discountRate: DiscountRate.of(snapshot.discountRate),
      issuedAt: this.now(),
    });
    await this.invoices.save(invoice);
    return invoice.invoiceNumber;
  }
}
