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

  selectEventType(eventType: 'RECEIVE' | 'LOAD' | 'UNLOAD' | 'CLAIM') {
    return this.page.locator(`#eventType_${eventType}`);
  }

  async fill(
    trackingNumber: string,
    eventType: 'RECEIVE' | 'LOAD' | 'UNLOAD' | 'CLAIM',
    completionTime: string,
    locationUnlocode: string,
    voyageNumber?: string,
    recipientConfirmation?: string
  ) {
    await this.trackingNumberInput.fill(trackingNumber);
    await this.selectEventType(eventType).check();
    await this.completionTimeInput.fill(completionTime);
    await this.locationUnlocodeInput.fill(locationUnlocode);
    if (voyageNumber) {
      await this.voyageNumberInput.fill(voyageNumber);
    }
    if (recipientConfirmation) {
      await this.page.locator('#recipientConfirmation').fill(recipientConfirmation);
    }
  }

  async submit() {
    await this.submitButton.click();
  }
}

export class TrackingDetailPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly statusBadge: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.statusBadge = page.locator('.badge');
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto(trackingNumber: string) {
    await this.page.goto(`/tracking/${trackingNumber}`);
  }

  async gotoPublic(trackingNumber: string) {
    await this.page.goto(`/public/tracking/${trackingNumber}`);
  }

  getTrackingNumberText() {
    return this.page.locator('body');
  }
}

export class ExceptionPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly trackingNumberInput: Locator;
  readonly exceptionTypeSelect: Locator;
  readonly locationUnlocodeInput: Locator;
  readonly occurrenceTimeInput: Locator;
  readonly reasonTextarea: Locator;
  readonly responseNoteTextarea: Locator;
  readonly submitButton: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.trackingNumberInput = page.locator('#trackingNumber');
    this.exceptionTypeSelect = page.locator('#exceptionType');
    this.locationUnlocodeInput = page.locator('#locationUnlocode');
    this.occurrenceTimeInput = page.locator('#occurrenceTime');
    this.reasonTextarea = page.locator('#reason');
    this.responseNoteTextarea = page.locator('#responseNote');
    this.submitButton = page.locator('button[type="submit"]', { hasText: '例外を記録する' });
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto() {
    await this.page.goto('/tracking/exception');
  }

  async fill(
    trackingNumber: string,
    exceptionType: 'DELAY' | 'DAMAGE' | 'LOST',
    locationUnlocode: string,
    occurrenceTime: string,
    reason?: string,
    responseNote?: string
  ) {
    await this.trackingNumberInput.fill(trackingNumber);
    await this.exceptionTypeSelect.selectOption(exceptionType);
    await this.locationUnlocodeInput.fill(locationUnlocode);
    await this.occurrenceTimeInput.fill(occurrenceTime);
    if (reason) {
      await this.reasonTextarea.fill(reason);
    }
    if (responseNote) {
      await this.responseNoteTextarea.fill(responseNote);
    }
  }

  async submit() {
    await this.submitButton.click();
  }
}

export class StatusUpdatePage {
  readonly page: Page;
  readonly heading: Locator;
  readonly trackingNumberInput: Locator;
  readonly newStatusSelect: Locator;
  readonly locationUnlocodeInput: Locator;
  readonly updateTimeInput: Locator;
  readonly submitButton: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.trackingNumberInput = page.locator('#trackingNumber');
    this.newStatusSelect = page.locator('#newStatus');
    this.locationUnlocodeInput = page.locator('#locationUnlocode');
    this.updateTimeInput = page.locator('#updateTime');
    this.submitButton = page.locator('button[type="submit"]', { hasText: '更新する' });
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto() {
    await this.page.goto('/tracking/status');
  }

  async fill(
    trackingNumber: string,
    newStatus: string,
    locationUnlocode: string,
    updateTime: string
  ) {
    await this.trackingNumberInput.fill(trackingNumber);
    await this.newStatusSelect.selectOption(newStatus);
    await this.locationUnlocodeInput.fill(locationUnlocode);
    await this.updateTimeInput.fill(updateTime);
  }

  async submit() {
    await this.submitButton.click();
  }
}
