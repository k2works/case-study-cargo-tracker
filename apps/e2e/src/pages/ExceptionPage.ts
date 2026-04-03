import { Page } from '@playwright/test';

export interface CargoExceptionData {
  trackingNumber: string;
  exceptionType: 'DELAY' | 'DAMAGE' | 'LOSS';
  locationCode?: string;
  occurredAt: string;
  reason?: string;
  resolution?: string;
}

export class ExceptionPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoNew(trackingNumber?: string) {
    const suffix = trackingNumber ? `?trackingNumber=${trackingNumber}` : '';
    await this.page.goto(`/exceptions/new${suffix}`);
  }

  async register(data: CargoExceptionData) {
    await this.gotoNew(data.trackingNumber);
    await this.page.locator('input[name="trackingNumber"]').fill(data.trackingNumber);
    await this.page.locator('select[name="exceptionType"]').selectOption(data.exceptionType);
    if (data.locationCode !== undefined) {
      await this.page.locator('input[name="locationCode"]').fill(data.locationCode);
    }
    await this.page.locator('input[name="occurredAt"]').fill(data.occurredAt);
    if (data.reason !== undefined) {
      await this.page.locator('textarea[name="reason"]').fill(data.reason);
    }
    if (data.resolution !== undefined) {
      await this.page.locator('[data-testid="exception-resolution"]').fill(data.resolution);
    }
    await this.page.locator('[data-testid="submit-exception"]').click();
  }
}
