import { Decimal } from 'decimal.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import type { InvoiceRepository } from '../../domain/repository/invoice-repository.js';
import { Invoice } from '../../domain/model/invoice.js';
import { InvoiceId } from '../../domain/model/invoice-id.js';
import { BillingBookingId } from '../../domain/model/billing-booking-id.js';
import { BillingShipperId } from '../../domain/model/billing-shipper-id.js';
import { Money } from '../../domain/model/money.js';
import { DiscountRate } from '../../domain/model/discount-rate.js';
import { InvoiceLineItem } from '../../domain/model/invoice-line-item.js';
import { PaymentStatus, isPaymentStatus } from '../../domain/model/payment-status.js';
import { BillingValidationError } from '../../domain/model/billing-validation-error.js';

/** 入金記録の支払方法既定値（詳細な決済手段は精算サービス（グループ 4）で扱う） */
const DEFAULT_PAYMENT_METHOD = 'BANK_TRANSFER';

interface InvoiceRow {
  id: number;
  invoiceNumber: string;
  bookingId: string;
  shipperId: string;
  shipperType: string;
  baseAmountValue: number;
  baseAmountCurrency: string;
  totalAmountValue: number;
  totalAmountCurrency: string;
  taxRate: string;
  taxAmount: string;
  paymentStatus: string;
  issuedAt: Date | null;
  dueDate: Date | null;
  discountAmountValue: number | null;
  discountAmountCurrency: string | null;
}

/** InvoiceRepository の Kysely 実装 */
export class KyselyInvoiceRepository implements InvoiceRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(invoice: Invoice): Promise<void> {
    await this.db.transaction().execute(async (trx) => {
      const inserted = await trx
        .insertInto('invoice')
        .values(this.toInvoiceValues(invoice))
        .returning('id')
        .executeTakeFirstOrThrow();
      await this.insertLineItems(trx, inserted.id, invoice);
    });
  }

  async update(invoice: Invoice): Promise<void> {
    await this.db.transaction().execute(async (trx) => {
      const row = await trx
        .updateTable('invoice')
        .set({
          totalAmountValue: invoice.finalAmount.amount.toNumber(),
          totalAmountCurrency: invoice.finalAmount.currency,
          taxAmount: invoice.taxAmount.amount.toString(),
          paymentStatus: invoice.paymentStatus,
          discountAmountValue: invoice.discountAmount.amount.toNumber(),
          discountAmountCurrency: invoice.discountAmount.currency,
          updatedAt: new Date(),
        })
        .where('invoiceNumber', '=', invoice.invoiceNumber)
        .returning('id')
        .executeTakeFirstOrThrow();

      // 明細は差分でなく全入替（発行時に確定した内訳をそのまま反映する）
      await trx.deleteFrom('invoice_line_item').where('invoiceId', '=', row.id).execute();
      await this.insertLineItems(trx, row.id, invoice);

      // 入金確認済みへの遷移時は支払記録を追記する（未記録の場合のみ）
      if (invoice.paymentStatus === PaymentStatus.CONFIRMED && invoice.paidAt !== null) {
        const existing = await trx
          .selectFrom('payment')
          .select('id')
          .where('invoiceId', '=', row.id)
          .executeTakeFirst();
        if (existing === undefined) {
          await trx
            .insertInto('payment')
            .values({
              invoiceId: row.id,
              paidAmountValue: invoice.finalAmount.amount.toNumber(),
              paidAmountCurrency: invoice.finalAmount.currency,
              paidAt: invoice.paidAt,
              paymentMethod: invoice.paymentMethod ?? DEFAULT_PAYMENT_METHOD,
              transactionReference: invoice.transactionReference,
            })
            .execute();
        }
      }
    });
  }

  async findByInvoiceId(): Promise<Invoice | null> {
    throw new BillingValidationError(
      'invoice テーブルは業務キーとして invoice_number / booking_id を保持する。findByInvoiceId は未対応',
    );
  }

  async findByInvoiceNumber(invoiceNumber: string): Promise<Invoice | null> {
    const row = await this.db
      .selectFrom('invoice')
      .selectAll()
      .where('invoiceNumber', '=', invoiceNumber)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    return this.reconstruct(row);
  }

  async findByBookingId(bookingId: string): Promise<Invoice | null> {
    const row = await this.db
      .selectFrom('invoice')
      .selectAll()
      .where('bookingId', '=', bookingId)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    return this.reconstruct(row);
  }

  async findAll(): Promise<Invoice[]> {
    const rows = await this.db.selectFrom('invoice').selectAll().orderBy('id', 'asc').execute();
    return Promise.all(rows.map((row) => this.reconstruct(row)));
  }

  private async insertLineItems(
    trx: AppDatabase,
    invoiceId: number,
    invoice: Invoice,
  ): Promise<void> {
    if (invoice.lineItems.length === 0) {
      return;
    }
    await trx
      .insertInto('invoice_line_item')
      .values(
        invoice.lineItems.map((item, index) => ({
          invoiceId,
          description: item.description,
          amountValue: item.amount.amount.toNumber(),
          amountCurrency: item.amount.currency,
          seqNumber: index + 1,
        })),
      )
      .execute();
  }

  private async reconstruct(row: InvoiceRow): Promise<Invoice> {
    if (!isPaymentStatus(row.paymentStatus)) {
      throw new BillingValidationError(`永続化された支払状態が不正です: ${row.paymentStatus}`);
    }
    const lineItemRows = await this.db
      .selectFrom('invoice_line_item')
      .selectAll()
      .where('invoiceId', '=', row.id)
      .orderBy('seqNumber', 'asc')
      .execute();

    const baseAmount = Money.of(row.baseAmountValue, row.baseAmountCurrency);
    const discountValue = row.discountAmountValue ?? 0;
    const rate =
      discountValue > 0
        ? new Decimal(discountValue).dividedBy(row.baseAmountValue)
        : new Decimal(0);

    return Invoice.reconstruct({
      invoiceId: InvoiceId.of(row.invoiceNumber),
      invoiceNumber: row.invoiceNumber,
      cargoBookingId: BillingBookingId.of(row.bookingId),
      shipperId: BillingShipperId.of(row.shipperId, row.shipperType),
      baseAmount,
      discountRate: DiscountRate.of(rate),
      taxRate: new Decimal(row.taxRate),
      finalAmount: Money.of(row.totalAmountValue, row.totalAmountCurrency),
      taxAmount: Money.of(new Decimal(row.taxAmount), row.totalAmountCurrency),
      paymentStatus: row.paymentStatus,
      issuedAt: row.issuedAt ?? new Date(),
      dueDate: row.dueDate ?? new Date(),
      paidAt: null,
      lineItems: lineItemRows.map((item) =>
        InvoiceLineItem.of(item.description, Money.of(item.amountValue, item.amountCurrency)),
      ),
    });
  }

  private toInvoiceValues(invoice: Invoice) {
    return {
      invoiceNumber: invoice.invoiceNumber,
      bookingId: invoice.cargoBookingId.value,
      shipperId: invoice.shipperId.value,
      shipperType: invoice.shipperId.shipperType,
      baseAmountValue: invoice.baseAmount.amount.toNumber(),
      baseAmountCurrency: invoice.baseAmount.currency,
      totalAmountValue: invoice.finalAmount.amount.toNumber(),
      totalAmountCurrency: invoice.finalAmount.currency,
      taxRate: invoice.taxRate.toString(),
      taxAmount: invoice.taxAmount.amount.toString(),
      paymentStatus: invoice.paymentStatus,
      issuedAt: invoice.issuedAt,
      dueDate: invoice.dueDate,
      discountAmountValue: invoice.discountAmount.amount.toNumber(),
      discountAmountCurrency: invoice.discountAmount.currency,
    };
  }
}
