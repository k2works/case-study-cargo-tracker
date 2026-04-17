import { Page, Locator } from '@playwright/test';

export class HandlingPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly trackingNumberInput: Locator;
  readonly completionTimeInput: Locator;
  readonly locationUnlocodeInput: Locator;
  readonly voyageNumberInput: Locator;
  readonly submitButton: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.trackingNumberInput = page.locator('#trackingNumber');
    this.completionTimeInput = page.locator('#completionTime');
    this.locationUnlocodeInput = page.locator('#locationUnlocode');
    this.voyageNumberInput = page.locator('#voyageNumber');
    this.submitButton = page.locator('button[type="submit"]', { hasText: '記録する' });
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto() {
    await this.page.goto('/tracking/handling');
  }

  selectEventType(eventType: 'RECEIVE' | 'LOAD' | 'UNLOAD') {
    return this.page.locator(`#eventType_${eventType}`);
  }

  async fill(
    trackingNumber: string,
    eventType: 'RECEIVE' | 'LOAD' | 'UNLOAD',
    completionTime: string,
    locationUnlocode: string,
    voyageNumber?: string
  ) {
    await this.trackingNumberInput.fill(trackingNumber);
    await this.selectEventType(eventType).check();
    await this.completionTimeInput.fill(completionTime);
    await this.locationUnlocodeInput.fill(locationUnlocode);
    if (voyageNumber) {
      await this.voyageNumberInput.fill(voyageNumber);
    }
  }

  async submit() {
    await this.submitButton.click();
  }
}
