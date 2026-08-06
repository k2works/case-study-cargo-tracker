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
  readonly confirmButton: Locator;
  readonly cancelButton: Locator;
  readonly routingButton: Locator;
  readonly routeAssignLink: Locator;
  readonly issueTrackingButton: Locator;
  readonly successAlert: Locator;
  readonly errorAlert: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.backButton = page.locator('a.btn-outline-secondary', { hasText: '一覧へ戻る' });
    this.confirmButton = page.locator('button.btn-success', { hasText: '予約確定' });
    this.cancelButton = page.locator('button.btn-outline-danger', { hasText: 'キャンセル' });
    this.routingButton = page.locator('button.btn-primary', { hasText: '経路設計依頼' });
    this.routeAssignLink = page.locator('a.btn-primary', { hasText: '経路を割り当て' });
    this.issueTrackingButton = page.locator('button.btn-info', { hasText: '追跡番号を発行' });
    this.successAlert = page.locator('.alert-success');
    this.errorAlert = page.locator('.alert-danger');
  }

  getDetailValue(label: string): Locator {
    return this.page.locator('dt', { hasText: label }).locator('~ dd').first();
  }

  async clickConfirm() {
    await this.confirmButton.click();
    await this.page.locator('#confirmModal').waitFor({ state: 'visible' });
    await this.page.locator('#confirmModal button.btn-success').click();
    await this.page.waitForURL(/\/bookings\//);
  }

  async clickCancel() {
    await this.cancelButton.click();
    await this.page.locator('#cancelModal').waitFor({ state: 'visible' });
    await this.page.locator('#cancelModal button.btn-danger').click();
    await this.page.waitForURL(/\/bookings\//);
  }

  async clickAssignToRouting() {
    await this.routingButton.click();
    await this.page.locator('#routingModal').waitFor({ state: 'visible' });
    await this.page.locator('#routingModal button.btn-primary').click();
    await this.page.waitForURL(/\/bookings\//);
  }

  async clickRouteAssign() {
    await this.routeAssignLink.click();
    await this.page.waitForURL(/\/bookings\/.+\/route/);
  }

  async clickIssueTracking() {
    await this.issueTrackingButton.click();
    await this.page.locator('#issueTrackingModal').waitFor({ state: 'visible' });
    await this.page.locator('#issueTrackingModal button.btn-info').click();
    await this.page.waitForURL(/\/bookings\//);
  }
}

export class BookingRoutePage {
  readonly page: Page;
  readonly heading: Locator;
  readonly backToDetailLink: Locator;
  // 検索条件フォーム
  readonly originInput: Locator;
  readonly destinationInput: Locator;
  readonly arrivalDeadlineInput: Locator;
  readonly searchButton: Locator;
  // 航路一覧
  readonly voyagesTable: Locator;
  readonly noResultsMessage: Locator;
  // 経路確定ボタン
  readonly confirmRouteButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.locator('h1');
    this.backToDetailLink = page.locator('a.btn-outline-secondary', { hasText: '詳細へ戻る' });
    this.originInput = page.locator('#originUnlocode');
    this.destinationInput = page.locator('#destinationUnlocode');
    this.arrivalDeadlineInput = page.locator('#arrivalDeadline');
    this.searchButton = page.locator('button[type="submit"]', { hasText: '条件変更して再検索' });
    this.voyagesTable = page.locator('table');
    this.noResultsMessage = page.locator('div.text-center.text-secondary');
    this.confirmRouteButton = page.locator('button[type="submit"]', { hasText: 'この航路で経路を確定する' });
  }

  async selectVoyage(voyageNumber: string) {
    await this.page.locator(`input[type="radio"][name="voyageNumber"][value="${voyageNumber}"]`).click();
  }

  async reSearch(originUnlocode: string, destinationUnlocode: string, arrivalDeadline: string) {
    await this.originInput.fill(originUnlocode);
    await this.destinationInput.fill(destinationUnlocode);
    await this.arrivalDeadlineInput.fill(arrivalDeadline);
    await this.searchButton.click();
    await this.page.waitForURL(/\/bookings\/.+\/route/);
  }

  async submitRoute() {
    await this.confirmRouteButton.click();
    await this.page.waitForURL(/\/bookings\/[^/]+$/);
  }
}
