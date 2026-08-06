import { Page, Locator } from '@playwright/test';

export interface ShipperFormData {
  name: string;
  email: string;
  phone: string;
  address: string;
  shipperType: 'Individual' | 'Corporate';
  contractNumber?: string;
  /** 0.00 〜 0.30（割合） */
  discountRate?: number;
}

export class ShipperPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoList(): Promise<void> {
    await this.page.goto('/shippers');
  }

  async gotoNew(): Promise<void> {
    await this.page.goto('/shippers/new');
  }

  async fillForm(data: ShipperFormData): Promise<void> {
    await this.page.locator('input[name="name"]').fill(data.name);
    await this.page.locator('input[name="email"]').fill(data.email);
    await this.page.locator('input[name="phone"]').fill(data.phone);
    await this.page.locator('input[name="address"]').fill(data.address);
    await this.page.locator('select[name="shipperType"]').selectOption(data.shipperType);
    if (data.shipperType === 'Corporate') {
      if (data.contractNumber !== undefined) {
        await this.page.locator('input[name="contractNumber"]').fill(data.contractNumber);
      }
      if (data.discountRate !== undefined) {
        await this.page
          .locator('input[name="discountRate"]')
          .fill(String(data.discountRate));
      }
    }
  }

  async submit(): Promise<void> {
    await this.page.locator('button[type="submit"]:has-text("登録")').click();
  }
}
