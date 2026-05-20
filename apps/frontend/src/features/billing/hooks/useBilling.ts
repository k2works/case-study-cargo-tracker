import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../../stores/authStore'
import type {
  CalculateChargeRequest,
  CalculateChargeResponse,
  Invoice,
  IssueInvoiceRequest,
  RecordPaymentRequest,
} from '../types/billing'

// billingApiBaseUrl は env に存在しないため、trackingApiBaseUrl と同様のパターンで
// VITE_BILLING_API_BASE_URL を参照する（env.ts は修正不要）。
const billingApiBaseUrl =
  import.meta.env.VITE_BILLING_API_BASE_URL ??
  import.meta.env.VITE_API_BASE_URL ??
  ''

async function fetchInvoices(token: string): Promise<Invoice[]> {
  const res = await fetch(`${billingApiBaseUrl}/api/v1/billing/invoices`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`請求一覧の取得に失敗しました (${res.status})`)
  return res.json()
}

async function fetchInvoice(invoiceId: string, token: string): Promise<Invoice> {
  const res = await fetch(`${billingApiBaseUrl}/api/v1/billing/invoices/${invoiceId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (res.status === 404) throw new Error('請求情報が見つかりません')
  if (!res.ok) throw new Error(`請求詳細の取得に失敗しました (${res.status})`)
  return res.json()
}

async function postCalculateCharge(
  invoiceId: string,
  body: CalculateChargeRequest,
  token: string,
): Promise<CalculateChargeResponse> {
  const res = await fetch(
    `${billingApiBaseUrl}/api/v1/billing/invoices/${invoiceId}/calculate`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    },
  )
  if (!res.ok) throw new Error(`料金算出に失敗しました (${res.status})`)
  return res.json()
}

async function fetchOverdueInvoices(token: string): Promise<Invoice[]> {
  const res = await fetch(`${billingApiBaseUrl}/api/v1/billing/invoices/overdue`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`督促一覧の取得に失敗しました (${res.status})`)
  return res.json()
}

async function postIssueInvoice(
  invoiceId: string,
  body: IssueInvoiceRequest,
  token: string,
): Promise<{ invoiceId: string; status: string }> {
  const res = await fetch(
    `${billingApiBaseUrl}/api/v1/billing/invoices/${invoiceId}/issue`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    },
  )
  if (!res.ok) throw new Error(`精算書発行に失敗しました (${res.status})`)
  return res.json()
}

async function patchSettleInvoice(
  invoiceId: string,
  body: RecordPaymentRequest,
  token: string,
): Promise<{ invoiceId: string; status: string }> {
  const res = await fetch(
    `${billingApiBaseUrl}/api/v1/billing/invoices/${invoiceId}/settle`,
    {
      method: 'PATCH',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    },
  )
  if (!res.ok) throw new Error(`精算完了処理に失敗しました (${res.status})`)
  return res.json()
}

/**
 * S22 請求一覧（経理担当者）。
 *
 * `GET /api/v1/billing/invoices` を呼び出し Invoice 一覧を取得する。
 */
export function useInvoiceList() {
  const token = useAuthStore((s) => s.token) ?? ''
  return useQuery({
    queryKey: ['invoices'],
    queryFn: () => fetchInvoices(token),
    enabled: !!token,
  })
}

/**
 * S23 請求詳細（経理担当者）。
 *
 * `GET /api/v1/billing/invoices/{invoiceId}` を呼び出し Invoice 詳細を取得する。
 */
export function useInvoice(invoiceId: string) {
  const token = useAuthStore((s) => s.token) ?? ''
  return useQuery({
    queryKey: ['invoices', invoiceId],
    queryFn: () => fetchInvoice(invoiceId, token),
    enabled: !!token && !!invoiceId,
  })
}

/**
 * S23 料金算出（経理担当者）。
 *
 * `POST /api/v1/billing/invoices/{invoiceId}/calculate` を呼び出し料金を算出・確定する。
 * 成功時は一覧と詳細の両キャッシュを無効化する。
 */
export function useCalculateCharge() {
  const token = useAuthStore((s) => s.token) ?? ''
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      invoiceId,
      body,
    }: {
      invoiceId: string
      body: CalculateChargeRequest
    }) => postCalculateCharge(invoiceId, body, token),
    onSuccess: (_, { invoiceId }) => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      queryClient.invalidateQueries({ queryKey: ['invoices', invoiceId] })
    },
  })
}

/**
 * S25 督促一覧（経理担当者）。
 */
export function useOverdueInvoiceList() {
  const token = useAuthStore((s) => s.token) ?? ''
  return useQuery({
    queryKey: ['invoices', 'overdue'],
    queryFn: () => fetchOverdueInvoices(token),
    enabled: !!token,
  })
}

/**
 * S24 精算書発行（経理担当者）。
 */
export function useIssueInvoice() {
  const token = useAuthStore((s) => s.token) ?? ''
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ invoiceId, body }: { invoiceId: string; body: IssueInvoiceRequest }) =>
      postIssueInvoice(invoiceId, body, token),
    onSuccess: (_, { invoiceId }) => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      queryClient.invalidateQueries({ queryKey: ['invoices', invoiceId] })
    },
  })
}

/**
 * US23 入金確認・精算完了（経理担当者）。
 */
export function useSettleInvoice() {
  const token = useAuthStore((s) => s.token) ?? ''
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ invoiceId, body }: { invoiceId: string; body: RecordPaymentRequest }) =>
      patchSettleInvoice(invoiceId, body, token),
    onSuccess: (_, { invoiceId }) => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      queryClient.invalidateQueries({ queryKey: ['invoices', invoiceId] })
    },
  })
}
