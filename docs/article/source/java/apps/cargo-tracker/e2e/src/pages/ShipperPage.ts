import { Page, Locator } from '@playwright/test';

export class ShipperIndexPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly registerButton: Locator;
  readonly table: Locator;
  readonly emptyMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.registerButton = page.locator('a.btn-primary', { hasText: '荷主を登録' });
    this.table = page.locator('table');
    this.emptyMessage = page.locator('td.text-center');
  }

  async goto() {
    await this.page.goto('/shippers');
  }

  async clickRegister() {
    await this.registerButton.click();
  }

  async getShipperRowByName(name: string): Promise<Locator> {
    return this.page.locator('tr', { hasText: name });
  }

  async clickDetailByName(name: string) {
    const row = await this.getShipperRowByName(name);
    await row.locator('a.btn-outline-primary').click();
  }
}

export class ShipperNewPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly nameInput: Locator;
  readonly emailInput: Locator;
  readonly phoneInput: Locator;
  readonly shipperTypeSelect: Locator;
  readonly contractNumberInput: Locator;
  readonly discountRateInput: Locator;
  readonly submitButton: Locator;
  readonly backButton: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.nameInput = page.locator('#name');
    this.emailInput = page.locator('#email');
    this.phoneInput = page.locator('#phone');
    this.shipperTypeSelect = page.locator('#shipperType');
    this.contractNumberInput = page.locator('#contractNumber');
    this.discountRateInput = page.locator('#discountRate');
    this.submitButton = page.locator('button[type="submit"]', { hasText: '登録する' });
    this.backButton = page.locator('a.btn-outline-secondary', { hasText: '一覧へ戻る' });
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto() {
    await this.page.goto('/shippers/new');
  }

  async fillIndividual(name: string, email: string, phone: string) {
    await this.nameInput.fill(name);
    await this.emailInput.fill(email);
    await this.phoneInput.fill(phone);
    await this.shipperTypeSelect.selectOption('INDIVIDUAL');
  }

  async fillCorporate(name: string, email: string, phone: string, contractNumber: string, discountRate: string) {
    await this.nameInput.fill(name);
    await this.emailInput.fill(email);
    await this.phoneInput.fill(phone);
    await this.shipperTypeSelect.selectOption('CORPORATE');
    await this.contractNumberInput.fill(contractNumber);
    await this.discountRateInput.fill(discountRate);
  }

  async submit() {
    await this.submitButton.click();
  }
}

export class ShipperShowPage {
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
