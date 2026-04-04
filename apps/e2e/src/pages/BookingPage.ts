import { Page, Locator } from '@playwright/test';

export class BookingIndexPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly registerButton: Locator;
  readonly table: Locator;
  readonly emptyMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.registerButton = page.locator('a.btn-primary', { hasText: '予約を登録' });
    this.table = page.locator('table');
    this.emptyMessage = page.locator('td.text-center');
  }

  async goto() {
    await this.page.goto('/bookings');
  }

  async clickRegister() {
    await this.registerButton.click();
  }

  async getBookingRowByOrigin(origin: string): Promise<Locator> {
    return this.page.locator('tr', { hasText: origin });
  }

  async clickDetailByOrigin(origin: string) {
    const row = await this.getBookingRowByOrigin(origin);
    await row.locator('a.btn-outline-primary').click();
  }
}

export class BookingNewPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly shipperIdInput: Locator;
  readonly cargoTypeSelect: Locator;
  readonly weightInput: Locator;
  readonly arrivalDeadlineInput: Locator;
  readonly originUnlocodeInput: Locator;
  readonly destinationUnlocodeInput: Locator;
  readonly submitButton: Locator;
  readonly backButton: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.shipperIdInput = page.locator('#shipperId');
    this.cargoTypeSelect = page.locator('#cargoType');
    this.weightInput = page.locator('#weight');
    this.arrivalDeadlineInput = page.locator('#arrivalDeadline');
    this.originUnlocodeInput = page.locator('#originUnlocode');
    this.destinationUnlocodeInput = page.locator('#destinationUnlocode');
    this.submitButton = page.locator('button[type="submit"]', { hasText: '登録する' });
    this.backButton = page.locator('a.btn-outline-secondary', { hasText: '一覧へ戻る' });
    this.errorAlert = page.locator('.alert-danger');
  }

  async goto() {
    await this.page.goto('/bookings/new');
  }

  async fill(shipperId: string, cargoType: string, weight: string, originUnlocode: string, destinationUnlocode: string, arrivalDeadline: string) {
    await this.shipperIdInput.fill(shipperId);
    await this.cargoTypeSelect.selectOption(cargoType);
    await this.weightInput.fill(weight);
    await this.originUnlocodeInput.fill(originUnlocode);
    await this.destinationUnlocodeInput.fill(destinationUnlocode);
    await this.arrivalDeadlineInput.fill(arrivalDeadline);
  }

  async submit() {
    await this.submitButton.click();
  }
}

export class BookingShowPage {
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
