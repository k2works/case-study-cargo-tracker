import { Page } from '@playwright/test';

export interface EstimateFormData {
  origin: string;
  destination: string;
  deadline: string;
  cargoType: 'General' | 'Refrigerated' | 'Hazardous';
  weightKg: number;
}

export class EstimatePage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoNew(): Promise<void> {
    await this.page.goto('/estimates/new');
  }

  async fillForm(data: EstimateFormData): Promise<void> {
    await this.page.locator('input[name="origin"]').fill(data.origin);
    await this.page.locator('input[name="destination"]').fill(data.destination);
    await this.page.locator('input[name="deadline"]').fill(data.deadline);
    await this.page.locator('select[name="cargoType"]').selectOption(data.cargoType);
    await this.page.locator('input[name="weightKg"]').fill(String(data.weightKg));
  }

  async submit(): Promise<void> {
    await this.page.locator('button[type="submit"]:has-text("見積算出")').click();
  }
}
