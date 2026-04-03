import { expect, Locator, Page } from '@playwright/test';

export class TrackingPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goto(trackingNumber: string) {
    await this.page.goto(`/tracking/${trackingNumber}`);
  }

  async trackingNumberText(): Promise<string> {
    const code = this.page.locator('[data-testid="tracking-number"]');
    await code.waitFor({ state: 'visible' });
    return code.innerText();
  }

  currentState(): Locator {
    return this.page.locator('[data-testid="current-state"]');
  }

  historyRows(): Locator {
    return this.page.locator('[data-testid="handling-history-table"] tbody tr');
  }

  exceptionRows(): Locator {
    return this.page.locator('[data-testid="exception-history-table"] tbody tr');
  }

  historyRow(index: number): Locator {
    return this.historyRows().nth(index);
  }

  exceptionRow(index: number): Locator {
    return this.exceptionRows().nth(index);
  }

  async expectHistoryEmpty() {
    await expect(this.page.locator('body')).toContainText('荷役履歴はまだありません。');
  }

  async expectExceptionHistoryEmpty() {
    await expect(this.page.locator('body')).toContainText('例外対応履歴はまだありません。');
  }

  async expectHistoryRow(index: number, params: {
    locationCode: string;
    eventType: string;
    memo?: string;
  }) {
    const row = this.historyRow(index);
    await expect(row).toContainText(params.locationCode);
    await expect(row).toContainText(params.eventType);
    if (params.memo) {
      await expect(row).toContainText(params.memo);
    }
  }

  async expectExceptionRow(index: number, params: {
    locationCode: string;
    exceptionType: string;
    reason?: string;
    resolution?: string;
    shipperNotificationStatus?: string;
  }) {
    const row = this.exceptionRow(index);
    await expect(row).toContainText(params.locationCode);
    await expect(row).toContainText(params.exceptionType);
    if (params.reason) {
      await expect(row).toContainText(params.reason);
    }
    if (params.resolution) {
      await expect(row).toContainText(params.resolution);
    }
    if (params.shipperNotificationStatus) {
      await expect(row).toContainText(params.shipperNotificationStatus);
    }
  }
}
