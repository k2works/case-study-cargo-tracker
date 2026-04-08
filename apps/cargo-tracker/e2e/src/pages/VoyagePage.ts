import { Page, Locator } from '@playwright/test';

export class VoyageIndexPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly table: Locator;
  readonly originUnlocodeInput: Locator;
  readonly destinationUnlocodeInput: Locator;
  readonly searchButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.table = page.locator('table');
    this.originUnlocodeInput = page.locator('#originUnlocode');
    this.destinationUnlocodeInput = page.locator('#destinationUnlocode');
    this.searchButton = page.locator('button[type="submit"]', { hasText: '検索' });
  }

  async goto() {
    await this.page.goto('/voyages');
  }

  async search(originUnlocode: string, destinationUnlocode: string) {
    if (originUnlocode) await this.originUnlocodeInput.fill(originUnlocode);
    if (destinationUnlocode) await this.destinationUnlocodeInput.fill(destinationUnlocode);
    await this.searchButton.click();
  }
}
