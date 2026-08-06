import { Page } from '@playwright/test';

export interface VoyageFormInput {
  voyageNumber: string;
  /** YYYY-MM-DDTHH:MM 形式（datetime-local 用、UTC） */
  departureLocation: string;
  arrivalLocation: string;
  departureTime: string;
  arrivalTime: string;
}

export class VoyagePage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoList(): Promise<void> {
    await this.page.goto('/voyages');
  }

  async gotoNew(): Promise<void> {
    await this.page.goto('/voyages/new');
  }

  async gotoEdit(voyageNumber: string): Promise<void> {
    await this.page.goto(`/voyages/${voyageNumber}/edit`);
  }

  async fillRegister(data: VoyageFormInput): Promise<void> {
    await this.page.locator('input[name="voyageNumber"]').fill(data.voyageNumber);
    await this.fillFirstMovement(data);
  }

  async fillFirstMovement(data: VoyageFormInput): Promise<void> {
    await this.page
      .locator('input[name="movements[0].departureLocation"]')
      .fill(data.departureLocation);
    await this.page
      .locator('input[name="movements[0].arrivalLocation"]')
      .fill(data.arrivalLocation);
    await this.page
      .locator('input[name="movements[0].departureTime"]')
      .fill(data.departureTime);
    await this.page
      .locator('input[name="movements[0].arrivalTime"]')
      .fill(data.arrivalTime);
  }

  async submitRegister(): Promise<void> {
    await this.page.locator('button[type="submit"]:has-text("登録")').click();
  }

  async submitUpdate(): Promise<void> {
    await this.page.locator('button[type="submit"]:has-text("更新する")').click();
  }
}
