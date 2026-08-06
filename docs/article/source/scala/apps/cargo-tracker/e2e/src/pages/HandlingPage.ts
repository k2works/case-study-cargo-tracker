import { Page } from '@playwright/test';

export type HandlingEventType = 'Receive' | 'Load' | 'Unload' | 'Customs' | 'Claim';

export interface HandlingFormData {
  trackingNumber: string;
  eventType: HandlingEventType;
  completionDateTime: string; // 'yyyy-MM-ddTHH:mm'
  locationUnLocode: string;
  voyageNumber?: string;
  operatorName?: string;
  recipientConfirmation?: string;
}

/** 荷役作業画面（US15） */
export class HandlingPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async gotoList(): Promise<void> {
    await this.page.goto('/handling');
  }

  async gotoNew(): Promise<void> {
    await this.page.goto('/handling/new');
  }

  async fillForm(data: HandlingFormData): Promise<void> {
    await this.page.locator('input[name="trackingNumber"]').fill(data.trackingNumber);
    await this.page.locator(`input[name="eventType"][value="${data.eventType}"]`).check();
    await this.page.locator('input[name="completionDateTime"]').fill(data.completionDateTime);
    await this.page.locator('input[name="locationUnLocode"]').fill(data.locationUnLocode);
    if (data.voyageNumber !== undefined) {
      await this.page.locator('input[name="voyageNumber"]').fill(data.voyageNumber);
    }
    if (data.operatorName !== undefined) {
      await this.page.locator('input[name="operatorName"]').fill(data.operatorName);
    }
    if (data.recipientConfirmation !== undefined) {
      await this.page.locator('input[name="recipientConfirmation"]').fill(data.recipientConfirmation);
    }
  }

  async submit(): Promise<void> {
    await this.page.locator('button[type="submit"]:has-text("登録")').click();
  }
}
