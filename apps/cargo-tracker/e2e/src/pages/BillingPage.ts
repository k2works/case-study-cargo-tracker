import { Page, Locator } from '@playwright/test';

export class BillingIndexPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly table: Locator;
  readonly emptyMessage: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.table = page.locator('table');
    this.emptyMessage = page.locator('.text-center.text-secondary');
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto() {
    await this.page.goto('/billing/invoices');
  }

  async getInvoiceRowByBookingId(bookingId: string): Promise<Locator> {
    return this.page.locator('tr', { hasText: bookingId });
  }

  async clickDetailByBookingId(bookingId: string) {
    const row = await this.getInvoiceRowByBookingId(bookingId);
    await row.locator('a.btn-outline-secondary').click();
  }
}

export class BillingShowPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly backButton: Locator;
  readonly confirmButton: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.backButton = page.locator('a.btn-outline-secondary', { hasText: '一覧へ戻る' });
    this.confirmButton = page.locator('a.btn-primary', { hasText: '入金確認' });
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  getDetailValue(label: string): Locator {
    return this.page.locator('dt', { hasText: label }).locator('~ dd').first();
  }

  async clickConfirm() {
    await this.confirmButton.click();
    await this.page.waitForURL(/\/billing\/invoices\/.+\/confirm/);
  }
}

export class BillingConfirmPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly backButton: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.backButton = page.locator('a.btn-outline-secondary', { hasText: '詳細へ戻る' });
    this.submitButton = page.locator('button[type="submit"]', { hasText: '入金確認する' });
  }

  getDetailValue(label: string): Locator {
    return this.page.locator('dt', { hasText: label }).locator('~ dd').first();
  }

  async submit() {
    await this.submitButton.click();
    await this.page.waitForURL(/\/billing\/invoices\/[^/]+$/);
  }
}
