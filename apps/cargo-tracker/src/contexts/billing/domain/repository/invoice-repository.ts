import type { Invoice } from '../model/invoice.js';

/** 請求書リポジトリ（出力ポート） */
export interface InvoiceRepository {
  /** 新規請求書を発行時に永続化する（明細を含む）。二重請求（同一 booking）は UNIQUE 制約で拒否される */
  save(invoice: Invoice): Promise<void>;
  /** 状態遷移・金額・明細を更新する。入金確認済み（CONFIRMED）遷移時は支払記録を追記する */
  update(invoice: Invoice): Promise<void>;
  findByInvoiceId(invoiceId: string): Promise<Invoice | null>;
  findByBookingId(bookingId: string): Promise<Invoice | null>;
  findAll(): Promise<Invoice[]>;
}
