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
  /** 調整の識別子（IT14 引き継ぎ C）。取り消す操作の宛先。調整以外は null。 */
  readonly adjustmentId?: string | null;
  /** すでに取り消されたか。**取り消し済みに取り消しを出さない。** */
  readonly reversed?: boolean;
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
   * **見積を経ない予約では `null`**（注 N12）。`null` のときは概算行も差額も
   * 出さない——出すと「見積が無い」ことを「差額 0」と読み違える。
   */
  readonly quotedAmount: number | null;
  /** 発行日。**未発行なら null**（US23 §1）。 */
  readonly issuedOn?: string | null;
  /** 支払期限（発行日 + 30 日）。**未発行なら null**。 */
  readonly dueOn?: string | null;
  readonly paidAt?: string | null;
  /**
   * 支払期限を過ぎているか。
   *
   * **列ではなくサーバが問い合わせのたびに数える**（不変条件 4）。期限当日は
   * 超過ではない。画面で数え直すと、業務タイムゾーンの扱いが 2 か所になる。
   */
  readonly overdue?: boolean;
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
): Promise<{ adjustmentId: string }> {
  return commandClient(`/billing/invoices/${encodeURIComponent(invoiceId)}/adjustments`, input);
}

/**
 * 入れた調整を取り消す（IT14 引き継ぎ C）。
 *
 * <p><b>消さずに反対向きを積む。</b> 何が起きたかを追えない記録は、経理に
 * とって根拠にならない。理由は必須。</p>
 */
export function reverseAdjustment(
  invoiceId: string,
  adjustmentId: string,
  reason: string,
): Promise<void> {
  return commandClient(
    `/billing/invoices/${encodeURIComponent(invoiceId)}`
    + `/adjustments/${encodeURIComponent(adjustmentId)}/reversal`,
    { reason },
  );
}

/**
 * 金額の表示。
 *
 * <p><b>実体は共有に移した</b>（`@/shared/ui/money`）。見積（S13）も金額を
 * 出すようになり、機能ごとに書式を持つと同じ額が画面によって違う見た目に
 * なる。ここは既存の import を壊さないための再輸出である。</p>
 */
export { formatMoney } from '@/shared/ui/money';

/**
 * 請求書を発行する（US23 §受入基準 1）。
 *
 * <p>支払期限（発行日 + 30 日）は集約が決める。画面は発行後に読み直す。</p>
 */
export function issueInvoice(invoiceId: string): Promise<void> {
  return commandClient(`/billing/invoices/${encodeURIComponent(invoiceId)}/issue`, {});
}

/**
 * 入金を記録する（US23 §受入基準 3・4）。
 *
 * <p><b>決済機関との接続はスコープ外。</b> 経理担当者が入金明細を見て記録する。
 * <b>入金日時は入金のあった時刻</b>で、記録した時刻ではない（記録は後日に
 * なることがある）。</p>
 */
export function recordPayment(
  invoiceId: string,
  input: { amount: number; paidAt: string },
): Promise<void> {
  return commandClient(`/billing/invoices/${encodeURIComponent(invoiceId)}/payments`, input);
}

/**
 * 請求書を取り消す（UC18）。
 *
 * <p><b>理由は必須。</b> 取り消した請求書は荷主にも見えなくなるので、何が
 * 起きたかを追えなければ、あとから誰も確かめられない。</p>
 *
 * <p><b>取り消したら再発行しない</b>（不変条件 6）。出し直すときは新規に発行する。</p>
 */
export function voidInvoice(invoiceId: string, reason: string): Promise<void> {
  return commandClient(`/billing/invoices/${encodeURIComponent(invoiceId)}/void`, { reason });
}
