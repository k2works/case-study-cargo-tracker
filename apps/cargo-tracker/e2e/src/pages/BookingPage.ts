import { Page } from '@playwright/test';

export interface BookingFormData {
  shipperCode: string;
  origin: string;
  destination: string;
  arrivalDeadline: string;
  cargoType: 'General' | 'Refrigerated' | 'Hazardous';
  weightKg: number;
  description?: string;
  quantity?: number;
  hazardousClass?: string;
  hazardousUnNumber?: string;
  hazardousProperName?: string;
  refrigerationMinTemp?: number;
  refrigerationMaxTemp?: number;
  refrigerationUnit?: 'Celsius' | 'Fahrenheit';
}

export class BookingPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoNew(): Promise<void> {
    await this.page.goto('/bookings/new');
  }

  async fillForm(data: BookingFormData): Promise<void> {
    await this.page.locator('input[name="shipperCode"]').fill(data.shipperCode);
    await this.page.locator('input[name="origin"]').fill(data.origin);
    await this.page.locator('input[name="destination"]').fill(data.destination);
    await this.page.locator('input[name="arrivalDeadline"]').fill(data.arrivalDeadline);
    await this.page.locator('select[name="cargoType"]').selectOption(data.cargoType);
    await this.page.locator('input[name="weightKg"]').fill(String(data.weightKg));
    if (data.description !== undefined) {
      await this.page.locator('input[name="description"]').fill(data.description);
    }
    if (data.quantity !== undefined) {
      await this.page.locator('input[name="quantity"]').fill(String(data.quantity));
    }
    if (data.cargoType === 'Hazardous') {
      if (data.hazardousClass) {
        await this.page.locator('input[name="hazardousClass"]').fill(data.hazardousClass);
      }
      if (data.hazardousUnNumber) {
        await this.page
          .locator('input[name="hazardousUnNumber"]')
          .fill(data.hazardousUnNumber);
      }
      if (data.hazardousProperName) {
        await this.page
          .locator('input[name="hazardousProperName"]')
          .fill(data.hazardousProperName);
      }
    }
    if (data.cargoType === 'Refrigerated') {
      if (data.refrigerationMinTemp !== undefined) {
        await this.page
          .locator('input[name="refrigerationMinTemp"]')
          .fill(String(data.refrigerationMinTemp));
      }
      if (data.refrigerationMaxTemp !== undefined) {
        await this.page
          .locator('input[name="refrigerationMaxTemp"]')
          .fill(String(data.refrigerationMaxTemp));
      }
      if (data.refrigerationUnit) {
        await this.page
          .locator('select[name="refrigerationUnit"]')
          .selectOption(data.refrigerationUnit);
      }
    }
  }

  async submit(): Promise<void> {
    await this.page.locator('button[type="submit"]:has-text("予約登録")').click();
  }
}
