import { Page, Locator } from '@playwright/test';

export class VoyagePage {
  readonly page: Page;
  readonly heading: Locator;
  readonly newVoyageLink: Locator;
  readonly voyageNumberInput: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole('heading', { name: '航海スケジュール管理' });
    this.newVoyageLink = page.getByRole('link', { name: '新規登録' });
    this.voyageNumberInput = page.getByLabel('航海番号');
    this.submitButton = page.getByRole('button', { name: '登録' });
  }

  async goto() {
    await this.page.goto('/voyages');
  }

  async fillVoyageForm(voyageNumber: string) {
    await this.voyageNumberInput.fill(voyageNumber);
    // 運航区間の必須フィールドを入力
    await this.page.getByPlaceholder('出発地 UN/LOCODE').fill('JPTYO');
    await this.page.getByPlaceholder('到着地 UN/LOCODE').fill('CNSHA');
    const [depInput, arrInput] = await this.page.locator('input[type="datetime-local"]').all();
    await depInput.fill('2025-01-10T08:00');
    await arrInput.fill('2025-01-12T18:00');
  }

  async submitForm() {
    await this.submitButton.click();
  }
}
