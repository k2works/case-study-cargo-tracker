import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/** 請求の状態（domain-model.md の要素表が正典）。 */
export type BillingStatus = 'PENDING' | 'CALCULATED' | 'INVOICED' | 'PAID' | 'VOID';

/** 明細の種別（`invoice_line_item.item_type`）。 */
export type LineItemType = 'BASE' | 'DISCOUNT' | 'ADJUSTMENT' | 'CANCELLATION_FEE' | 'TAX';

export interface InvoiceLineView {
  readonly itemType: LineItemType;
  /** 呼び名はサーバが返す。**画面で対応表を持たない**——2 か所になると片方だけ直る。 */
  readonly itemTypeLabel: string;
  /** 根拠の文（「2 区間・近海 2.5 + 遠洋 6.0・1,200 kg・一般 1.0」）。 */
  readonly description: string;
  readonly amount: number;
  readonly currency: string;
  /** 調整の根拠になった例外 ID。**S42 の例外へ飛ぶ**（US28 §8 の受け側）。 */
  readonly basisExceptionId: string | null;
}

export interface InvoiceSummaryView {
  readonly invoiceId: string;
  readonly bookingId: string;
  readonly shipperId: string;
  readonly shipperName: string | null;
  readonly shipperTypeLabel: string;
  readonly status: BillingStatus;
  readonly statusLabel: string;
  readonly totalAmount: number;
  readonly currency: string;
  readonly calculatedAt: string;
}

export interface InvoiceView {
  readonly invoiceId: string;
  readonly bookingId: string;
  readonly shipperId: string;
  readonly shipperName: string | null;
  readonly shipperType: string;
  readonly shipperTypeLabel: string;
  readonly contractNumber: string | null;
  readonly discountRate: number | null;
  readonly baseAmount: number;
  readonly discountAmount: number;
  readonly adjustmentAmount: number;
  readonly taxAmount: number;
  readonly totalAmount: number;
  readonly currency: string;
  readonly status: BillingStatus;
  readonly statusLabel: string;
  readonly calculatedAt: string;
  /**
   * 見積時の概算。
   *
   * **いまは必ず `null`**——見積は US01（IT14）なので、見積を経ない予約しかない。
   * `null` のときは概算行も差額も出さない（出すと「見積が無い」ことを
   * 「差額 0」と読み違える）。
   */
  readonly quotedAmount: number | null;
  readonly lineItems: readonly InvoiceLineView[];
}

/**
 * 請求一覧（S60 / US21）。
 *
 * **既定で入金済・取消を外す。** 決着したものが混ざると、一覧全体が「まだ手を
 * 入れる場所」に見えなくなる。並びはサーバが決める（算出日時の新しい順）。
 */
export function fetchInvoices(
  includeSettled: boolean,
  bookingId?: string | null,
): Promise<Pending<{ items: InvoiceSummaryView[]; total: number }>> {
  const query = new URLSearchParams({ includeSettled: includeSettled ? 'true' : 'false' });
  if (bookingId !== undefined && bookingId !== null && bookingId.trim() !== '') {
    query.set('bookingId', bookingId.trim());
  }
  return queryClient(`/billing/invoices?${query.toString()}`);
}

/** 請求書 1 通（S61）。 */
export function fetchInvoice(invoiceId: string): Promise<Pending<InvoiceView>> {
  return queryClient(`/billing/invoices/${encodeURIComponent(invoiceId)}`);
}

/** その予約の有効な請求書（S22 予約詳細から飛ぶ）。 */
export function fetchInvoiceOfBooking(bookingId: string): Promise<Pending<InvoiceView>> {
  return queryClient(`/billing/invoices/by-booking/${encodeURIComponent(bookingId)}`);
}

/**
 * 料金を調整する（S61 / US21 §受入基準 6）。
 *
 * **符号で向きを表す**——減額は負、補償費用は正。理由は必須。
 */
export function adjustInvoice(
  invoiceId: string,
  input: { amount: number; reason: string; basisExceptionId: string | null },
): Promise<void> {
  return commandClient(`/billing/invoices/${encodeURIComponent(invoiceId)}/adjustments`, input);
}

/** 金額の表示（画面はどこでも同じ書き方にする）。 */
export function formatMoney(amount: number, currency: string): string {
  const formatted = new Intl.NumberFormat('ja-JP').format(Math.abs(amount));
  const sign = amount < 0 ? '− ' : '';
  return `${sign}${currency === 'JPY' ? '¥ ' : `${currency} `}${formatted}`;
}
