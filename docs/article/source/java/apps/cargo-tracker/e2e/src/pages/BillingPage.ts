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

export class FreightCalculationPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly bookingIdInput: Locator;
  readonly searchButton: Locator;
  readonly adjustmentAmountInput: Locator;
  readonly adjustmentReasonInput: Locator;
  readonly confirmButton: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.bookingIdInput = page.locator('#bookingId');
    this.searchButton = page.locator('button[type="submit"]', { hasText: '算出する' });
    this.adjustmentAmountInput = page.locator('#adjustmentAmount');
    this.adjustmentReasonInput = page.locator('#adjustmentReason');
    this.confirmButton = page.locator('button[type="submit"]', { hasText: '料金を確定する' });
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto() {
    await this.page.goto('/billing/freight-calculation');
  }

  async searchByBookingId(bookingId: string) {
    await this.bookingIdInput.fill(bookingId);
    await this.searchButton.click();
    await this.page.waitForURL(/\/billing\/freight-calculation\?bookingId=.+/);
  }

  async confirmFreight(adjustmentAmount: number = 0, adjustmentReason: string = '') {
    await this.adjustmentAmountInput.fill(adjustmentAmount.toString());
    if (adjustmentReason) {
      await this.adjustmentReasonInput.fill(adjustmentReason);
    }
    await this.confirmButton.click();
    await this.page.waitForURL('/billing/invoices');
  }
}
