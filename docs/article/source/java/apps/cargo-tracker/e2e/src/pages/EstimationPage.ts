import { Page, Locator } from '@playwright/test';

export class EstimateIndexPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly newButton: Locator;
  readonly table: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.newButton = page.locator('a.btn-primary', { hasText: '新規見積作成' });
    this.table = page.locator('table');
  }

  async goto() {
    await this.page.goto('/estimates');
  }
}

export class EstimateNewPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly originUnlocodeInput: Locator;
  readonly destinationUnlocodeInput: Locator;
  readonly arrivalDeadlineInput: Locator;
  readonly cargoTypeSelect: Locator;
  readonly weightKgInput: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.originUnlocodeInput = page.locator('#originUnlocode');
    this.destinationUnlocodeInput = page.locator('#destinationUnlocode');
    this.arrivalDeadlineInput = page.locator('#arrivalDeadline');
    this.cargoTypeSelect = page.locator('#cargoType');
    this.weightKgInput = page.locator('#weightKg');
    this.submitButton = page.locator('button[type="submit"]', { hasText: '候補を検索して保存' });
    this.cancelButton = page.locator('a.btn-outline-secondary', { hasText: 'キャンセル' });
  }

  async goto() {
    await this.page.goto('/estimates/new');
  }

  async fill(originUnlocode: string, destinationUnlocode: string, arrivalDeadline: string, cargoType: string, weightKg: string) {
    await this.originUnlocodeInput.fill(originUnlocode);
    await this.destinationUnlocodeInput.fill(destinationUnlocode);
    await this.arrivalDeadlineInput.fill(arrivalDeadline);
    await this.cargoTypeSelect.selectOption(cargoType);
    await this.weightKgInput.fill(weightKg);
  }

  async submit() {
    await this.submitButton.click();
  }
}

export class EstimateShowPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly backButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.backButton = page.locator('a.btn-outline-secondary', { hasText: '一覧へ戻る' });
  }

  getDetailValue(label: string): Locator {
    return this.page.locator('dt', { hasText: label }).locator('~ dd').first();
  }
}
