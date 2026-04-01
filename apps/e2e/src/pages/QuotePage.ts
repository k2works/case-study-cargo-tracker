import { expect, Locator, Page } from '@playwright/test';

export interface QuoteData {
  originLocode: string;
  destinationLocode: string;
  requestedArrivalDate: string;
  cargoType: string;
  weightKg: string;
}

export class QuotePage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goto() {
    await this.page.goto('/quotes/new');
  }

  async gotoList() {
    await this.page.goto('/quotes');
  }

  async register(data: QuoteData) {
    await this.goto();
    await this.page.locator('input[name="originLocode"]').fill(data.originLocode);
    await this.page.locator('input[name="destinationLocode"]').fill(data.destinationLocode);
    await this.page.locator('input[name="requestedArrivalDate"]').fill(data.requestedArrivalDate);
    await this.page.locator('select[name="cargoType"]').selectOption(data.cargoType);
    await this.page.locator('input[name="weightKg"]').fill(data.weightKg);
    await this.page.locator('form[action="/quotes"] button[type="submit"]').click();
  }

  async getSuccessMessage(): Promise<string> {
    const alert = this.page.locator('.alert-success');
    await alert.waitFor({ state: 'visible' });
    return await alert.innerText();
  }

  async extractQuoteNumber(): Promise<string> {
    const message = await this.getSuccessMessage();
    const match = message.match(/見積番号:\s*([A-Z0-9-]+)/);
    if (!match) {
      throw new Error(`見積番号が成功メッセージから取得できませんでした: ${message}`);
    }
    return match[1];
  }

  routeCandidateCard(index: number): Locator {
    return this.page.locator('.card.shadow-sm.mb-3').nth(index);
  }

  quoteRow(quoteNumber: string): Locator {
    return this.page.locator('tbody tr').filter({ hasText: quoteNumber });
  }

  async expectRouteCandidateVisible(params: {
    index: number;
    voyageNumber: string;
    viaLocodesText: string;
    transitDaysText: string;
    estimatedPriceText: string;
  }) {
    const card = this.routeCandidateCard(params.index);
    await expect(card).toBeVisible();
    await expect(card).toContainText(params.voyageNumber);
    await expect(card).toContainText(params.viaLocodesText);
    await expect(card).toContainText(params.transitDaysText);
    await expect(card).toContainText(params.estimatedPriceText);
  }

  async expectQuoteListed(params: {
    quoteNumber: string;
    originLocode: string;
    destinationLocode: string;
    cargoTypeDisplayName: string;
    weightKg: string;
    requestedArrivalDate: string;
  }) {
    const row = this.quoteRow(params.quoteNumber);

    await expect(row).toHaveCount(1);
    await expect(row.locator('td').nth(0)).toContainText(params.quoteNumber);
    await expect(row.locator('td').nth(1)).toHaveText(params.originLocode);
    await expect(row.locator('td').nth(2)).toHaveText(params.destinationLocode);
    await expect(row.locator('td').nth(3)).toHaveText(params.cargoTypeDisplayName);
    await expect(row.locator('td').nth(4)).toContainText(params.weightKg);
    await expect(row.locator('td').nth(5)).toHaveText(params.requestedArrivalDate);
  }
}
