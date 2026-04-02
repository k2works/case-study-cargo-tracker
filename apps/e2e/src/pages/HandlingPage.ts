import { expect, Locator, Page } from '@playwright/test';

export interface HandlingEventData {
  bookingId: string;
  eventType: 'LOAD' | 'UNLOAD' | 'CUSTOMS' | 'TRANSHIP' | 'RECEIVE' | 'MANUAL_UPDATE';
  locationCode: string;
  completionTime: string;
  memo?: string;
  receiveConfirmationCode?: string;
}

export class HandlingPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoList() {
    await this.page.goto('/handling');
  }

  async gotoNew(bookingId?: string) {
    const suffix = bookingId ? `?bookingId=${bookingId}` : '';
    await this.page.goto(`/handling/new${suffix}`);
  }

  async gotoReceive(bookingId?: string) {
    const suffix = bookingId ? `?bookingId=${bookingId}` : '';
    await this.page.goto(`/handling/receive${suffix}`);
  }

  async gotoManualUpdate(bookingId?: string) {
    const suffix = bookingId ? `?bookingId=${bookingId}` : '';
    await this.page.goto(`/handling/manual-update${suffix}`);
  }

  async register(data: HandlingEventData) {
    await this.gotoNew(data.bookingId);
    await this.page.locator('input[name="bookingId"]').fill(data.bookingId);
    await this.page.locator('select[name="eventType"]').selectOption(data.eventType);
    await this.page.locator('input[name="locationCode"]').fill(data.locationCode);
    await this.page.locator('input[name="completionTime"]').fill(data.completionTime);
    if (data.memo) {
      await this.page.locator('textarea[name="memo"]').fill(data.memo);
    }
    await this.page.locator('form[action="/handling"] button[type="submit"]').click();
  }

  async searchByBookingId(bookingId: string) {
    await this.gotoList();
    await this.page.locator('input[name="bookingId"]').fill(bookingId);
    await this.page.locator('form[action="/handling"] button[type="submit"]').click();
  }

  async registerReceive(data: Omit<HandlingEventData, 'eventType'>) {
    await this.gotoReceive(data.bookingId);
    await this.page.locator('input[name="bookingId"]').fill(data.bookingId);
    await this.page.locator('input[name="locationCode"]').fill(data.locationCode);
    await this.page.locator('input[name="completionTime"]').fill(data.completionTime);
    if (data.receiveConfirmationCode !== undefined) {
      await this.page.locator('[data-testid="receive-confirmation-code"]').fill(data.receiveConfirmationCode);
    }
    if (data.memo) {
      await this.page.locator('textarea[name="memo"]').fill(data.memo);
    }
    await this.page.locator('[data-testid="submit-receive"]').click();
  }

  async registerManualUpdate(data: Omit<HandlingEventData, 'eventType'>) {
    await this.gotoManualUpdate(data.bookingId);
    await this.page.locator('input[name="bookingId"]').fill(data.bookingId);
    await this.page.locator('input[name="locationCode"]').fill(data.locationCode);
    await this.page.locator('input[name="completionTime"]').fill(data.completionTime);
    if (data.memo !== undefined) {
      await this.page.locator('[data-testid="memo-input"]').fill(data.memo);
    }
    await this.page.locator('[data-testid="submit-manual-update"]').click();
  }

  private displayEventType(eventType: string): string {
    const eventTypeLabels: Record<string, string> = {
      LOAD: '積み込み',
      UNLOAD: '荷降ろし',
      CUSTOMS: '通関',
      TRANSHIP: '積み替え',
      RECEIVE: '引取',
      MANUAL_UPDATE: '手動更新',
    };
    return eventTypeLabels[eventType] ?? eventType;
  }

  eventRow(bookingId: string, eventType: string): Locator {
    return this.page.locator('tbody tr')
      .filter({ hasText: bookingId })
      .filter({ hasText: this.displayEventType(eventType) });
  }

  async expectEventListed(params: {
    bookingId: string;
    eventType: string;
    locationCode: string;
    completionDateTime: string;
    memo?: string;
  }) {
    const row = this.eventRow(params.bookingId, params.eventType);

    await expect(row).toHaveCount(1);
    await expect(row).toContainText(params.bookingId);
    await expect(row).toContainText(this.displayEventType(params.eventType));
    await expect(row).toContainText(params.locationCode);
    await expect(row).toContainText(params.completionDateTime);
    await expect(row).toContainText(params.memo ?? '—');
  }
}
