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

  historyRows(): Locator {
    return this.page.locator('[data-testid="handling-history-table"] tbody tr');
  }

  historyRow(index: number): Locator {
    return this.historyRows().nth(index);
  }

  async expectHistoryEmpty() {
    await expect(this.page.locator('body')).toContainText('荷役履歴はまだありません。');
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
}
