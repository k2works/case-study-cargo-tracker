import { Page } from '@playwright/test';

/** Billing 系 (請求書 + 支払 + CSV 取込) のページオブジェクト。IT9 US22/US23/US29 で利用。 */
export class BillingPage {
  constructor(public readonly page: Page) {}

  async gotoInvoiceList(): Promise<void> {
    await this.page.goto('/billing/invoices');
  }

  async gotoNewInvoice(): Promise<void> {
    await this.page.goto('/billing/invoices/new');
  }

  /** US21: 請求書発行 (Delivered 予約から)。 */
  async generateInvoice(bookingId: string): Promise<void> {
    await this.gotoNewInvoice();
    await this.page.locator('input[name="bookingId"]').fill(bookingId);
    await this.page.locator('button[type="submit"]').click();
  }

  /** Invoice 詳細画面に直接遷移する。 */
  async gotoInvoiceDetail(invoiceId: string): Promise<void> {
    await this.page.goto(`/billing/invoices/${invoiceId}`);
  }

  /** US23 issuePayment: NotIssued → Pending。 */
  async issuePayment(invoiceId: string, dueDate: string, referenceCode: string): Promise<void> {
    await this.gotoInvoiceDetail(invoiceId);
    await this.page.locator('input[name="dueDate"]').fill(dueDate);
    await this.page.locator('input[name="referenceCode"]').fill(referenceCode);
    await this.page.locator('button:has-text("発行")').click();
  }

  /** US23 confirmPayment: Pending → Confirmed + Cargo.Settled 連携。 */
  async confirmPayment(invoiceId: string, paidAtIso: string): Promise<void> {
    await this.gotoInvoiceDetail(invoiceId);
    await this.page.locator('input[name="paidAt"]').fill(paidAtIso);
    // confirm dialog を自動で OK
    this.page.once('dialog', (d) => d.accept());
    await this.page.locator('button:has-text("入金確認")').click();
  }

  /** US29: CSV 取込フォーム遷移。 */
  async gotoImportCsv(): Promise<void> {
    await this.page.goto('/billing/payments/import');
  }

  /** US29: CSV アップロード + 一括取込実行。 */
  async uploadCsv(filePath: string): Promise<void> {
    await this.gotoImportCsv();
    await this.page.locator('input[name="csv"]').setInputFiles(filePath);
    await this.page.locator('button:has-text("一括入金確認")').click();
  }
}
