import type { PageResponse } from '../../../shared/api/types';
import { authHeader } from '../../../shared/api/auth';

/**
 * 請求書ステータス（domain-model.md L913-920 / iteration_plan-7 §405-408）。
 */
export type BillingStatus =
  | 'PENDING'
  | 'CALCULATED'
  | 'INVOICED'
  | 'PAID'
  | 'OVERDUE'
  | 'CANCELLED';

/** invoice_line.line_type（ADR-0015 派生、line_type 駆動設計）。 */
export type InvoiceLineType = 'BASIC' | 'DISCOUNT' | 'ADJUSTMENT' | 'SURCHARGE';

export interface InvoiceLine {
  lineSeq: number;
  lineType: InvoiceLineType;
  description: string;
  amount: string; // BigDecimal は JSON で文字列としてシリアライズ
  reasonCode: string | null;
}

export interface Invoice {
  invoiceId: string;
  bookingId: string;
  shipperId: string;
  basicAmount: string;
  discountAmount: string;
  adjustmentAmount: string;
  totalAmount: string;
  currency: string;
  billingStatus: BillingStatus;
  invoiceNumber: string | null;
  paymentDue: string | null; // ISO LocalDate（YYYY-MM-DD）
  paidAt: string | null;     // ISO LocalDateTime
  createdAt: string;
  updatedAt: string;
  lines: InvoiceLine[];
}

export interface CalculateInvoiceRequest {
  bookingId: string;
  shipperId: string;
  distanceKm: string;
  weightKg: string;
  cargoType: 'GENERAL' | 'HAZARDOUS' | 'REFRIGERATED' | string;
  handlingCount: number;
  currency: string;
}

export interface InvoiceCreationResponse {
  invoiceId: string;
}

/**
 * 請求詳細取得（US21 / US23、S23 表示用）。
 */
export async function fetchInvoice(invoiceId: string): Promise<Invoice> {
  const res = await fetch(`/api/v1/billing/invoices/${invoiceId}`, {
    headers: authHeader(),
  });
  if (res.status === 404) {
    throw new Error('NOT_FOUND');
  }
  if (!res.ok) throw new Error('請求書の取得に失敗しました');
  return res.json();
}

/**
 * 請求一覧（US23、S22 表示用、ページネーション付き）。
 * status 指定で billing_status による絞り込みが可能。
 */
export async function fetchInvoicesPage(
  page = 0,
  size = 20,
  status?: BillingStatus,
): Promise<PageResponse<Invoice>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  const res = await fetch(`/api/v1/billing/invoices?${params.toString()}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('請求一覧の取得に失敗しました');
  return res.json();
}

/**
 * 督促対象（INVOICED + payment_due 超過）一覧（US23 S25）。
 */
export async function fetchOverdueInvoices(): Promise<{
  items: Invoice[];
  totalCount: number;
  page: number;
  size: number;
}> {
  const res = await fetch('/api/v1/billing/invoices/overdue', {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('督促対象の取得に失敗しました');
  return res.json();
}

/**
 * 精算書発行（US23、S24「発行」ボタン押下時）。
 * Invoice 集約が InvoiceNumberGenerator で採番、PaymentDuePolicy で支払期限確定。
 * CALCULATED 状態でのみ受理。
 */
export async function issueInvoice(invoiceId: string): Promise<void> {
  const res = await fetch(`/api/v1/billing/invoices/${invoiceId}/issue`, {
    method: 'POST',
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('精算書発行に失敗しました');
}

/**
 * 入金記録（US23、S23「入金確認」ボタン）。
 * IT7 は完全一致のみ受理（paidAmount == totalAmount）。
 */
export async function recordPayment(
  invoiceId: string,
  request: {
    paidAmount: string;
    currency: string;
    paidAt?: string;
    paymentMethod?: 'BANK_TRANSFER' | 'CREDIT_CARD' | 'MANUAL';
    externalReference?: string;
  },
): Promise<{ paymentId: string }> {
  const res = await fetch(`/api/v1/billing/invoices/${invoiceId}/payments`, {
    method: 'POST',
    headers: { ...authHeader(), 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!res.ok) throw new Error('入金記録に失敗しました');
  return res.json();
}

/**
 * 輸送料金算出開始（US21、手動契機）。
 */
export async function calculateInvoice(
  request: CalculateInvoiceRequest,
): Promise<InvoiceCreationResponse> {
  const res = await fetch('/api/v1/billing/invoices', {
    method: 'POST',
    headers: { ...authHeader(), 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!res.ok) throw new Error('輸送料金算出に失敗しました');
  return res.json();
}

/**
 * 法人割引適用（US22、S23 「割引を適用」ボタン押下時）。
 *
 * <p>Invoice 集約が ShipperInfoAcl から契約を取得し CorporateDiscountPolicy で
 * 割引額を算出する。CALCULATED 状態でのみ受理（それ以外は 422）。</p>
 */
export async function applyDiscount(invoiceId: string): Promise<void> {
  const res = await fetch(`/api/v1/billing/invoices/${invoiceId}/discount`, {
    method: 'POST',
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('法人割引適用に失敗しました');
}

/** BillingStatus → 日本語ラベル変換（S23 / S22 表示用）。 */
export function billingStatusLabel(status: BillingStatus): string {
  switch (status) {
    case 'PENDING':
      return '算出待ち';
    case 'CALCULATED':
      return '算出済';
    case 'INVOICED':
      return '発行済';
    case 'PAID':
      return '入金済';
    case 'OVERDUE':
      return '未払';
    case 'CANCELLED':
      return 'キャンセル';
  }
}

/** InvoiceLineType → 日本語ラベル変換。 */
export function invoiceLineTypeLabel(type: InvoiceLineType): string {
  switch (type) {
    case 'BASIC':
      return '基本料金';
    case 'DISCOUNT':
      return '割引';
    case 'ADJUSTMENT':
      return '調整';
    case 'SURCHARGE':
      return '割増';
  }
}
