import type { PageResponse } from '../../../shared/api/types';
import { authHeader } from '../../../shared/api/auth';

/**
 * 請求書ステータス（domain-model.md L913-920 / iteration_plan-7 §405-408）。
 */
export type BillingStatus =
  | 'PENDING'
  | 'CALCULATED'
  | 'INVOICED'
  | 'PARTIALLY_PAID'
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
  /** 累積入金額（IT9 / US26、BalanceTracker の paidSoFar 投影。部分入金時は total_amount 未満） */
  paidSoFar?: string;
  currency: string;
  billingStatus: BillingStatus;
  invoiceNumber: string | null;
  paymentDue: string | null; // ISO LocalDate（YYYY-MM-DD）
  paidAt: string | null;     // ISO LocalDateTime
  createdAt: string;
  updatedAt: string;
  lines: InvoiceLine[];
}

/**
 * 入金履歴（US23 / IT9 / US26、S23 入金履歴セクション）。
 * isPartial=TRUE は Stripe webhook 経由の部分入金、FALSE は完全入金。
 */
export interface Payment {
  paymentId: string;
  invoiceId: string;
  paidAmount: string;
  currency: string;
  paidAt: string;
  paymentMethod: string | null;
  externalReference: string | null;
  isPartial: boolean;
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
 * 法人割引適用（US22、S23 「割引を適用」ボタン押下時 / IT8 T4.2 で手動入力対応）。
 *
 * <p>通常時（manualDiscountRate 未指定）: Invoice 集約が ShipperInfoAcl から契約を取得し
 * CorporateDiscountPolicy で割引額を算出する。Circuit Breaker OPEN 時のみ
 * {@code manualDiscountRate}（0.00〜0.30）を渡し、ACL バイパスで直接適用。
 * CALCULATED 状態でのみ受理（それ以外は 422）。</p>
 */
export async function applyDiscount(
  invoiceId: string,
  manualDiscountRate?: number,
): Promise<void> {
  const body =
    manualDiscountRate !== undefined
      ? JSON.stringify({ manualDiscountRate })
      : undefined;
  const res = await fetch(`/api/v1/billing/invoices/${invoiceId}/discount`, {
    method: 'POST',
    headers: { ...authHeader(), 'Content-Type': 'application/json' },
    body,
  });
  if (!res.ok) throw new Error('法人割引適用に失敗しました');
}

/** CircuitBreaker 状態。S23 で手動入力フォーム切替に使用（IT8 T4.2）。 */
export type CircuitBreakerState = 'CLOSED' | 'OPEN' | 'HALF_OPEN' | 'DISABLED' | 'FORCED_OPEN' | 'METRICS_ONLY';
export interface CircuitBreakerHealth {
  name: string;
  state: CircuitBreakerState;
  registered: boolean;
  failureRate?: number;
  bufferedCalls?: number;
}

/**
 * Resilience4j Circuit Breaker 現在状態を取得（IT8 T4.2）。
 * shipperInfo の state=OPEN なら S23 で手動入力フォームを表示する。
 */
export async function getCircuitBreakerHealth(name: string): Promise<CircuitBreakerHealth> {
  const res = await fetch(`/api/v1/billing/circuit-breakers/${name}`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('Circuit Breaker 状態取得に失敗しました');
  return (await res.json()) as CircuitBreakerHealth;
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
    case 'PARTIALLY_PAID':
      return '部分入金';
    case 'PAID':
      return '入金済';
    case 'OVERDUE':
      return '未払';
    case 'CANCELLED':
      return 'キャンセル';
  }
}

/**
 * 請求書ごとの入金履歴を取得（IT9 / US26、S23 入金履歴セクション）。
 * 既存の GET /api/v1/billing/invoices/{id}/payments を利用。
 */
export async function fetchPayments(invoiceId: string): Promise<Payment[]> {
  const res = await fetch(`/api/v1/billing/invoices/${invoiceId}/payments`, {
    headers: authHeader(),
  });
  if (!res.ok) throw new Error('入金履歴の取得に失敗しました');
  return res.json();
}

/**
 * Stripe ダッシュボードへの遷移 URL を生成（IT9 / US26）。
 * external_reference に Stripe Charge ID（ch_xxx / pi_xxx）が格納されている前提。
 */
export function stripeDashboardUrl(externalReference: string | null): string | null {
  if (!externalReference || externalReference.trim() === '') return null;
  return `https://dashboard.stripe.com/payments/${externalReference}`;
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
