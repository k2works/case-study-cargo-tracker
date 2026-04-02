import { test, expect } from '../fixtures';
import { QuotePage } from '../pages/QuotePage';

function futureDateStr(monthsAhead: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + monthsAhead);
  return d.toISOString().slice(0, 10);
}

test.describe('E05: 見積作成', () => {
  test('見積を作成すると詳細画面に遷移し、候補一覧が表示される', async ({ page, loggedIn }) => {
    const quotePage = new QuotePage(page);
    const arrivalDate = futureDateStr(2);
    const quoteData = {
      originLocode: 'JPTYO',
      destinationLocode: 'USNYC',
      requestedArrivalDate: arrivalDate,
      cargoType: 'GENERAL_CARGO',
      weightKg: '500.5',
    };

    await quotePage.goto();

    await expect(page.locator('h4')).toContainText('見積登録');
    await expect(page.locator('input[name="originLocode"]')).toBeVisible();
    await expect(page.locator('select[name="cargoType"]')).toBeVisible();

    await quotePage.register(quoteData);

    await expect(page).toHaveURL(/\/quotes\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/);
    await expect(page.locator('h4')).toContainText('見積詳細');
    await expect(page.locator('.alert-success')).toContainText('見積を登録しました');

    const quoteNumber = await quotePage.extractQuoteNumber();
    await expect(page.locator('h5 code')).toHaveText(quoteNumber);

    const quoteConditionCard = page.locator('.card.shadow-sm.mb-4');
    await expect(quoteConditionCard.locator('dd').filter({ hasText: /^JPTYO$/ })).toBeVisible();
    await expect(quoteConditionCard.locator('dd').filter({ hasText: /^USNYC$/ })).toBeVisible();
    await expect(quoteConditionCard.locator('dd').filter({ hasText: new RegExp(`^${arrivalDate}$`) })).toBeVisible();
    await expect(quoteConditionCard.locator('dd').filter({ hasText: /^一般貨物$/ })).toBeVisible();
    await expect(quoteConditionCard.locator('dd').filter({ hasText: /500\.5/ })).toBeVisible();

    await quotePage.expectRouteCandidateVisible({
      index: 0,
      voyageNumber: 'SG001',
      viaLocodesText: 'SGSIN → JPTYO',
      transitDaysText: '14 日',
      estimatedPriceText: '150,000 円',
    });
    await quotePage.expectRouteCandidateVisible({
      index: 1,
      voyageNumber: 'SG002',
      viaLocodesText: 'SGSIN → KRPUS → JPTYO',
      transitDaysText: '18 日',
      estimatedPriceText: '120,000 円',
    });
    await quotePage.expectRouteCandidateVisible({
      index: 2,
      voyageNumber: 'JP001',
      viaLocodesText: 'JPTYO',
      transitDaysText: '7 日',
      estimatedPriceText: '200,000 円',
    });
  });

  test('見積作成後に一覧画面で見積番号と条件を確認できる', async ({ page, loggedIn }) => {
    const quotePage = new QuotePage(page);
    const arrivalDate = futureDateStr(3);
    const quoteData = {
      originLocode: 'NLRTM',
      destinationLocode: 'JPTYO',
      requestedArrivalDate: arrivalDate,
      cargoType: 'REFRIGERATED',
      weightKg: '1200',
    };

    await quotePage.register(quoteData);

    const quoteNumber = await quotePage.extractQuoteNumber();

    await quotePage.gotoList();
    await expect(page).toHaveURL('/quotes');
    await expect(page.locator('h4')).toContainText('見積一覧');

    await quotePage.expectQuoteListed({
      quoteNumber,
      originLocode: quoteData.originLocode,
      destinationLocode: quoteData.destinationLocode,
      cargoTypeDisplayName: '冷凍・冷蔵',
      weightKg: quoteData.weightKg,
      requestedArrivalDate: arrivalDate,
    });
  });
});
