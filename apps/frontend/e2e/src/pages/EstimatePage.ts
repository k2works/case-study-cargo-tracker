import { Page, Locator } from '@playwright/test';

export class EstimatePage {
  readonly page: Page;
  readonly heading: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole('heading', { name: '輸送見積の作成' });
    this.submitButton = page.getByRole('button', { name: '見積を作成' });
  }

  async goto() {
    await this.page.goto('/estimates');
  }

  async fillEstimateForm(origin: string, destination: string, deadline: string, weightKg: string) {
    await this.page.getByPlaceholder('JPTYO').fill(origin);
    await this.page.getByPlaceholder('USNYC').fill(destination);
    await this.page.locator('input[type="date"]').fill(deadline);
    await this.page.locator('input[type="number"]').fill(weightKg);
  }

  async submitForm() {
    await this.submitButton.click();
  }
}
