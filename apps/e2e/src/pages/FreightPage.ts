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
  }) {
    await expect(this.summary()).toBeVisible();
    await expect(this.page.locator('[data-testid="freight-route-path"]')).toContainText(params.routePath);
    await expect(this.page.locator('[data-testid="freight-distance-km"]')).toContainText(params.distanceKm);
    await expect(this.page.locator('[data-testid="freight-weight-kg"]')).toContainText(params.weightKg);
    await expect(this.page.locator('[data-testid="freight-cargo-type"]')).toContainText(params.cargoType);
    await expect(this.page.locator('[data-testid="freight-handling-count"]')).toContainText(params.handlingCount);
  }

  async expectChargeRow(params: {
    bookingId: string;
    status: string;
    baseAmount: string;
    adjustmentAmount: string;
    totalAmount: string;
  }) {
    const row = this.rowByBookingId(params.bookingId);
    await expect(row).toContainText(params.bookingId);
    await expect(row).toContainText(params.status);
    await expect(row).toContainText(params.baseAmount);
    await expect(row).toContainText(params.adjustmentAmount);
    await expect(row).toContainText(params.totalAmount);
  }

  async confirmByBookingId(bookingId: string) {
    const row = this.rowByBookingId(bookingId);
    await row.locator('[data-testid="freight-confirm-submit"]').click();
  }
}
