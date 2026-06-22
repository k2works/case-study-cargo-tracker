import { Page } from '@playwright/test';

/** 追跡照会画面（US18） */
export class TrackingPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoInput(): Promise<void> {
    await this.page.goto('/tracking');
  }

  async lookup(trackingNumber: string): Promise<void> {
    await this.page.locator('input[name="trackingNumber"]').fill(trackingNumber);
    await this.page.locator('button[type="submit"]:has-text("照会")').click();
  }

  async gotoDetail(trackingNumber: string): Promise<void> {
    await this.page.goto(`/tracking/${trackingNumber}`);
  }

  async gotoPublic(trackingNumber: string): Promise<void> {
    await this.page.goto(`/public/tracking/${trackingNumber}`);
  }
}
