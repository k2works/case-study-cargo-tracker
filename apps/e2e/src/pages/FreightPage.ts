import { expect, Locator, Page } from '@playwright/test';

export class FreightPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoList() {
    await this.page.goto('/freight');
  }

  async gotoCalculate(bookingId?: string) {
    const suffix = bookingId ? `?bookingId=${bookingId}` : '';
    await this.page.goto(`/freight/calculate${suffix}`);
  }

  async calculate(params: {
    bookingId: string;
    adjustmentAmount?: string;
  }) {
    await this.gotoCalculate(params.bookingId);
    await this.page.locator('[data-testid="freight-booking-id"]').fill(params.bookingId);
    if (params.adjustmentAmount !== undefined) {
      await this.page.locator('[data-testid="freight-adjustment-amount"]').fill(params.adjustmentAmount);
    }
    await this.page.locator('[data-testid="freight-calculate-submit"]').click();
  }

  summary(): Locator {
    return this.page.locator('[data-testid="freight-booking-summary"]');
  }

  rowByBookingId(bookingId: string): Locator {
    return this.page.locator('[data-testid="freight-charge-row"]').filter({ hasText: bookingId }).first();
  }

  async expectSummary(params: {
    routePath: string;
    distanceKm: string;
    weightKg: string;
    cargoType: string;
    handlingCount: string;
    discountRate?: string;
    previewTotalAmount?: string;
  }) {
    await expect(this.summary()).toBeVisible();
    await expect(this.page.locator('[data-testid="freight-route-path"]')).toContainText(params.routePath);
    await expect(this.page.locator('[data-testid="freight-distance-km"]')).toContainText(params.distanceKm);
    await expect(this.page.locator('[data-testid="freight-weight-kg"]')).toContainText(params.weightKg);
    await expect(this.page.locator('[data-testid="freight-cargo-type"]')).toContainText(params.cargoType);
    await expect(this.page.locator('[data-testid="freight-handling-count"]')).toContainText(params.handlingCount);
    if (params.discountRate) {
      await expect(this.page.locator('[data-testid="freight-discount-rate"]')).toContainText(params.discountRate);
    }
    if (params.previewTotalAmount) {
      await expect(this.page.locator('[data-testid="freight-preview-total-amount"]')).toContainText(
        params.previewTotalAmount,
      );
    }
  }

  async expectChargeRow(params: {
    bookingId: string;
    status: string;
    baseAmount: string;
    adjustmentAmount: string;
    totalAmount: string;
    discountRate?: string;
  }) {
    const row = this.rowByBookingId(params.bookingId);
    const cells = row.locator('td');
    await expect(cells.nth(1)).toContainText(params.bookingId);
    await expect(cells.nth(2)).toContainText(params.status);
    await expect(cells.nth(3)).toContainText(params.baseAmount);
    await expect(cells.nth(5)).toContainText(params.adjustmentAmount);
    await expect(cells.nth(6)).toContainText(params.totalAmount);
    if (params.discountRate) {
      await expect(cells.nth(4)).toContainText(params.discountRate);
    }
  }

  async confirmByBookingId(bookingId: string) {
    const row = this.rowByBookingId(bookingId);
    this.page.once('dialog', (dialog) => dialog.accept());
    await row.locator('[data-testid="freight-confirm-submit"]').click();
  }

  async generateInvoiceByBookingId(bookingId: string, dueDate?: string) {
    const row = this.rowByBookingId(bookingId);
    if (dueDate) {
      await row.locator('[data-testid="freight-invoice-due-date"]').fill(dueDate);
    }
    await row.locator('[data-testid="freight-generate-invoice-submit"]').click();
  }
}
