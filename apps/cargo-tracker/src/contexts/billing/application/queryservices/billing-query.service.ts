import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { PaymentStatus } from '../../domain/model/payment-status.js';

/** 未請求（DELIVERED かつ請求書未発行）の予約サマリー（請求書一覧の発行候補） */
export interface UnbilledBookingSummary {
  bookingId: string;
  shipperName: string;
  shipperType: string;
  origin: string;
  destination: string;
  weightKg: number;
  cargoType: string;
}

/** 発行済み請求書のサマリー（請求書一覧） */
export interface InvoiceSummary {
  invoiceNumber: string;
  bookingId: string;
  totalAmountValue: number;
  totalAmountCurrency: string;
  paymentStatus: string;
  issuedAt: Date | null;
  dueDate: Date | null;
}

/** 請求書明細（基本料金・消費税・割引根拠・調整） */
export interface InvoiceLineItemView {
  description: string;
  amountValue: number;
  amountCurrency: string;
}

/** 入金記録（US23-4） */
export interface PaymentView {
  paidAmountValue: number;
  paidAmountCurrency: string;
  paidAt: Date;
  paymentMethod: string;
  transactionReference: string | null;
}

/** 請求書詳細（明細・支払状態・支払期限・入金記録。US23） */
export interface InvoiceDetailView {
  invoiceNumber: string;
  bookingId: string;
  shipperId: string;
  shipperType: string;
  baseAmountValue: number;
  totalAmountValue: number;
  totalAmountCurrency: string;
  taxAmount: number;
  discountAmountValue: number;
  paymentStatus: string;
  issuedAt: Date | null;
  dueDate: Date | null;
  lineItems: InvoiceLineItemView[];
  payments: PaymentView[];
}

/**
 * 精算の読み取りモデル（CQRS・ADR-010 の読みモデル例外に従い DB を直接参照する）。
 * 請求書一覧（発行候補 + 発行済み）・請求書詳細・ダッシュボードの未払い件数を提供する。
 */
export class BillingQueryService {
  constructor(private readonly db: AppDatabase) {}

  /** 引取済（DELIVERED）で請求書がまだ無い予約を返す（発行候補） */
  async listUnbilledDeliveries(): Promise<UnbilledBookingSummary[]> {
    const delivered = await this.db
      .selectFrom('cargo as c')
      .innerJoin('shipper as s', 's.id', 'c.shipperId')
      .select([
        'c.bookingId as bookingId',
        's.name as shipperName',
        's.shipperType as shipperType',
        'c.originUnlocode as origin',
        'c.destinationUnlocode as destination',
        'c.weight as weightKg',
        'c.cargoType as cargoType',
      ])
      .where('c.bookingStatus', '=', 'DELIVERED')
      .execute();

    const billed = new Set(
      (await this.db.selectFrom('invoice').select('bookingId').execute()).map((r) => r.bookingId),
    );

    return delivered
      .filter((row) => !billed.has(row.bookingId))
      .map((row) => ({
        bookingId: row.bookingId,
        shipperName: row.shipperName,
        shipperType: row.shipperType,
        origin: row.origin,
        destination: row.destination,
        weightKg: Number(row.weightKg),
        cargoType: row.cargoType,
      }));
  }

  /** 発行済み請求書の一覧（新しい順） */
  async listInvoices(): Promise<InvoiceSummary[]> {
    const rows = await this.db
      .selectFrom('invoice')
      .select([
        'invoiceNumber',
        'bookingId',
        'totalAmountValue',
        'totalAmountCurrency',
        'paymentStatus',
        'issuedAt',
        'dueDate',
      ])
      .orderBy('id', 'desc')
      .execute();
    return rows.map((row) => ({
      invoiceNumber: row.invoiceNumber,
      bookingId: row.bookingId,
      totalAmountValue: row.totalAmountValue,
      totalAmountCurrency: row.totalAmountCurrency,
      paymentStatus: row.paymentStatus,
      issuedAt: row.issuedAt,
      dueDate: row.dueDate,
    }));
  }

  /** 請求書詳細（明細・入金記録付き）。存在しなければ null */
  async getInvoiceDetail(invoiceNumber: string): Promise<InvoiceDetailView | null> {
    const invoice = await this.db
      .selectFrom('invoice')
      .selectAll()
      .where('invoiceNumber', '=', invoiceNumber)
      .executeTakeFirst();
    if (invoice === undefined) {
      return null;
    }
    const lineItems = await this.db
      .selectFrom('invoice_line_item')
      .select(['description', 'amountValue', 'amountCurrency'])
      .where('invoiceId', '=', invoice.id)
      .orderBy('seqNumber', 'asc')
      .execute();
    const payments = await this.db
      .selectFrom('payment')
      .select(['paidAmountValue', 'paidAmountCurrency', 'paidAt', 'paymentMethod', 'transactionReference'])
      .where('invoiceId', '=', invoice.id)
      .orderBy('id', 'asc')
      .execute();
    return {
      invoiceNumber: invoice.invoiceNumber,
      bookingId: invoice.bookingId,
      shipperId: invoice.shipperId,
      shipperType: invoice.shipperType,
      baseAmountValue: invoice.baseAmountValue,
      totalAmountValue: invoice.totalAmountValue,
      totalAmountCurrency: invoice.totalAmountCurrency,
      taxAmount: Number(invoice.taxAmount),
      discountAmountValue: invoice.discountAmountValue ?? 0,
      paymentStatus: invoice.paymentStatus,
      issuedAt: invoice.issuedAt,
      dueDate: invoice.dueDate,
      lineItems: lineItems.map((item) => ({
        description: item.description,
        amountValue: item.amountValue,
        amountCurrency: item.amountCurrency,
      })),
      payments: payments.map((p) => ({
        paidAmountValue: p.paidAmountValue,
        paidAmountCurrency: p.paidAmountCurrency,
        paidAt: p.paidAt,
        paymentMethod: p.paymentMethod,
        transactionReference: p.transactionReference,
      })),
    };
  }

  /** ダッシュボードの未払い（OVERDUE）件数（経理担当者カード用） */
  async countOverdue(): Promise<number> {
    const row = await this.db
      .selectFrom('invoice')
      .select((eb) => eb.fn.countAll<string>().as('count'))
      .where('paymentStatus', '=', PaymentStatus.OVERDUE)
      .executeTakeFirst();
    return row === undefined ? 0 : Number(row.count);
  }
}
