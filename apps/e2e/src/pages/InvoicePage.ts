import { expect, Page } from '@playwright/test';

export class InvoicePage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoList() {
    await this.page.goto('/invoices');
  }

  async expectLatestInvoiceRow(params: {
    bookingId: string;
    amount: string;
    dueDate?: string;
    paymentStatus?: string;
  }) {
    const row = this.page.locator('[data-testid="invoice-row"]').filter({ hasText: params.bookingId }).first();
    await expect(row).toContainText(params.bookingId);
    await expect(row).toContainText(params.amount);
    if (params.dueDate) {
      await expect(row).toContainText(params.dueDate);
    }
    if (params.paymentStatus) {
      await expect(row).toContainText(params.paymentStatus);
    }
    return row;
  }

  async openLatestInvoiceByBookingId(bookingId: string) {
    const row = await this.expectLatestInvoiceRow({ bookingId, amount: '' });
    await row.locator('a').first().click();
  }

  async confirmPaymentByBookingId(bookingId: string) {
    const row = await this.expectLatestInvoiceRow({ bookingId, amount: '' });
    await row.locator('[data-testid="invoice-confirm-payment-submit"]').click();
  }

  async expectInvoiceDetail(params: {
    bookingId: string;
    amount: string;
    dueDate: string;
    paymentStatus: string;
  }) {
    await expect(this.page.locator('[data-testid="invoice-booking-id"]')).toContainText(params.bookingId);
    await expect(this.page.locator('[data-testid="invoice-amount"]')).toContainText(params.amount);
    await expect(this.page.locator('[data-testid="invoice-due-date"]')).toContainText(params.dueDate);
    await expect(this.page.locator('[data-testid="invoice-payment-status"]')).toContainText(
      params.paymentStatus,
    );
  }

  async expectDiscountBreakdown(params: {
    baseAmount: string;
    discountRate: string;
    adjustmentAmount: string;
    totalAmount: string;
  }) {
    await expect(this.page.locator('[data-testid="invoice-discount-breakdown"]')).toBeVisible();
    await expect(this.page.locator('[data-testid="invoice-base-amount"]')).toContainText(params.baseAmount);
    await expect(this.page.locator('[data-testid="invoice-discount-rate"]')).toContainText(params.discountRate);
    await expect(this.page.locator('[data-testid="invoice-adjustment-amount"]')).toContainText(
      params.adjustmentAmount,
    );
    await expect(this.page.locator('[data-testid="invoice-total-amount"]')).toContainText(params.totalAmount);
  }
}
