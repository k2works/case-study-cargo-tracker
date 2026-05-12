import { Page, Locator } from '@playwright/test';

export class ShipperPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly newShipperLink: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole('heading', { name: '荷主一覧' });
    this.newShipperLink = page.getByRole('link', { name: '新規登録' });
    this.submitButton = page.getByRole('button', { name: '登録する' });
  }

  async goto() {
    await this.page.goto('/shippers');
  }

  async fillIndividualForm(name: string, email: string, phone: string) {
    await this.page.getByLabel('氏名/社名').fill(name);
    await this.page.getByLabel('メールアドレス').fill(email);
    await this.page.getByLabel('電話番号').fill(phone);
    await this.page.getByLabel('個人').check();
  }

  async fillCorporateForm(name: string, email: string, phone: string, contractNumber: string, discountRate: string) {
    await this.page.getByLabel('氏名/社名').fill(name);
    await this.page.getByLabel('メールアドレス').fill(email);
    await this.page.getByLabel('電話番号').fill(phone);
    await this.page.getByLabel('法人').check();
    await this.page.getByLabel('契約番号').fill(contractNumber);
    await this.page.getByLabel('割引率（0〜30%）').fill(discountRate);
  }

  async submitForm() {
    await this.submitButton.click();
  }
}
