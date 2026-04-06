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

  // 危険物フィールド用 locator
  readonly hazardousClassInput: Locator;
  readonly unNumberInput: Locator;
  readonly properShippingNameInput: Locator;

  // 冷凍・冷蔵フィールド用 locator
  readonly minTemperatureInput: Locator;
  readonly maxTemperatureInput: Locator;
  readonly temperatureUnitSelect: Locator;

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

    // 危険物フィールド
    this.hazardousClassInput = page.locator('#hazardousClass');
    this.unNumberInput = page.locator('#unNumber');
    this.properShippingNameInput = page.locator('#properShippingName');

    // 冷凍・冷蔵フィールド
    this.minTemperatureInput = page.locator('#minTemperature');
    this.maxTemperatureInput = page.locator('#maxTemperature');
    this.temperatureUnitSelect = page.locator('#temperatureUnit');
  }

  async goto() {
    await this.page.goto('/bookings/new');
  }

  async fill(shipperId: string, cargoType: string, weight: string, originUnlocode: string, destinationUnlocode: string, arrivalDeadline: string) {
    await this.shipperIdInput.selectOption(shipperId);
    await this.cargoTypeSelect.selectOption(cargoType);
    await this.weightInput.fill(weight);
    await this.originUnlocodeInput.fill(originUnlocode);
    await this.destinationUnlocodeInput.fill(destinationUnlocode);
    await this.arrivalDeadlineInput.fill(arrivalDeadline);
  }

  async fillHazardous(shipperId: string, weight: string, originUnlocode: string, destinationUnlocode: string, arrivalDeadline: string, hazardousClass: string, unNumber: string, properShippingName: string) {
    await this.shipperIdInput.selectOption(shipperId);
    await this.cargoTypeSelect.selectOption('HAZARDOUS');
    // 危険物セクションが表示されるのを待つ
    await this.hazardousClassInput.waitFor({ state: 'visible' });
    await this.hazardousClassInput.fill(hazardousClass);
    await this.unNumberInput.fill(unNumber);
    await this.properShippingNameInput.fill(properShippingName);
    await this.weightInput.fill(weight);
    await this.originUnlocodeInput.fill(originUnlocode);
    await this.destinationUnlocodeInput.fill(destinationUnlocode);
    await this.arrivalDeadlineInput.fill(arrivalDeadline);
  }

  async fillRefrigerated(shipperId: string, weight: string, originUnlocode: string, destinationUnlocode: string, arrivalDeadline: string, minTemperature: string, maxTemperature: string, temperatureUnit: string) {
    await this.shipperIdInput.selectOption(shipperId);
    await this.cargoTypeSelect.selectOption('REFRIGERATED');
    // 冷凍セクションが表示されるのを待つ
    await this.minTemperatureInput.waitFor({ state: 'visible' });
    await this.minTemperatureInput.fill(minTemperature);
    await this.maxTemperatureInput.fill(maxTemperature);
    await this.temperatureUnitSelect.selectOption(temperatureUnit);
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
