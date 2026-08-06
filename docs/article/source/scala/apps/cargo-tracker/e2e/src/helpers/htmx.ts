import { Page } from '@playwright/test';

/** htmx ポーリング・部分更新の完了を待機する。
 *  hx-request 属性が外れたタイミングで DOM 更新が完了したとみなす。 */
export async function waitForHtmxUpdate(
  page: Page,
  selector: string,
  timeout = 10000,
): Promise<void> {
  await page.waitForFunction(
    (sel) => {
      const el = document.querySelector(sel);
      return el !== null && !el.hasAttribute('hx-request');
    },
    selector,
    { timeout },
  );
}
