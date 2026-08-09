import { test, expect } from '@playwright/test';
import { localDate, localDateTime } from './support/time.js';



/**
 * クリティカルパス: 予約 → 引き渡し → 経路確定 → 予約確定 → 追跡番号 → 積込 → 輸送中
 * → 荷降し → 引取 → 配送完了 → 追跡照会。
 *
 * **IT7 で終点まで伸ばした。** IT6 では輸送が始まるところで終わっており、
 * **貨物が届いたことを誰も確かめられなかった**。
 *
 * **ここで確かめるのは「業務価値が実際に成立するか」だけである**
 * （`development_strategy.md` の横断方針 1）。個々の規則は単体・統合テストが
 * 見ており、E2E で繰り返さない。E2E が見るのは
 * **複数のロールをまたいで一本の業務が通ること**である。
 *
 * 4 人が順に登場する。営業担当者・経路設計者・営業担当者・追跡管理者・荷役作業員。
 * **ロールをまたぐ受け渡しは通知ではなく待ち行列で行う**（ADR-006）。
 * 待ち行列に現れることが、この業務における「引き渡し」である。
 */

const USERS = {
  sales: { username: 'sales', password: 'password' },
  router: { username: 'router', password: 'password' },
  tracker: { username: 'tracker', password: 'password' },
  handler: { username: 'handler', password: 'password' },
  // 荷主（US18）。**IT7 まではシードに存在しなかった**
  shipper: { username: 'shipper', password: 'password' },
};

/**
 * 荷役作業を 1 件登録する.
 * @param {import('@playwright/test').Page} page ページ
 * @param {object} work 作業内容
 */
async function registerHandling(page, work) {
  await page.getByRole('link', { name: '荷役管理' }).click();
  await page.getByRole('link', { name: '新規登録', exact: true }).click();

  await page.fill('#trackingNumber', work.trackingNumber);
  await page.selectOption('#type', work.type);
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', work.location);
  if (work.voyageNumber) {
    await page.fill('#voyageNumber', work.voyageNumber);
  }
  if (work.confirmationCode) {
    await page.fill('#confirmationCode', work.confirmationCode);
    await page.fill('#consigneeName', work.consigneeName);
  }
  await page.getByRole('button', { name: '登録する' }).click();
}

/**
 * ステータスバッジが指定の状態になっていることを確かめる.
 *
 * **フラッシュメッセージと取り違えない。** 「予約を登録しました（仮予約）」のような
 * 文にも状態名が含まれるため、素朴な文字列一致だと**操作の説明文を状態と誤認する**。
 * @param {import('@playwright/test').Page} page ページ
 * @param {string} label 状態の日本語ラベル
 */
async function expectStatusBadge(page, label) {
  await expect(page.locator('.badge', { hasText: label }).first()).toBeVisible();
}

/**
 * 指定した利用者でログインする（前の利用者はログアウトする）.
 * @param {import('@playwright/test').Page} page ページ
 * @param {{username: string, password: string}} user 利用者
 */
async function loginAs(page, user) {
  await page.goto('/login');
  // すでにログイン済みならダッシュボードへ飛ばされる。**その場合は一度出る**
  if (!page.url().includes('/login')) {
    await page.getByRole('button', { name: 'ログアウト' }).click();
    await page.goto('/login');
  }
  await page.fill('#username', user.username);
  await page.fill('#password', user.password);
  await page.getByRole('button', { name: 'ログイン' }).click();
  await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
}

test('予約から引き渡しと追跡照会までが一本つながる', async ({ page }) => {
  // **取り消せない操作には確認ダイアログがある**（経路の確定・キャンセル）。
  // 受け入れないとフォームが送信されず、押したつもりで何も起きない
  page.on('dialog', (dialog) => dialog.accept());

  // ---- 営業担当者: 予約を登録し、経路設計者に引き渡す ----
  await loginAs(page, USERS.sales);
  await page.getByRole('link', { name: '貨物予約' }).click();
  await page.getByRole('link', { name: '+ 新規予約登録' }).click();

  await page.fill('#shipperCode', 'SHP-000001');
  await page.selectOption('#cargoType', 'GENERAL');
  await page.fill('#weight', '1000');
  await page.fill('#origin', 'JPOSA');
  await page.fill('#destination', 'USLAX');
  await page.fill('#arrivalDeadline', localDate(60));
  await page.getByRole('button', { name: '登録する' }).click();

  await expectStatusBadge(page, '仮予約');
  const detailUrl = page.url();

  await page.getByRole('button', { name: '経路設計者に引き渡す' }).click();
  await expectStatusBadge(page, '経路提案済');

  // ---- 経路設計者: 候補を算出し、経路を確定する ----
  await loginAs(page, USERS.router);
  // **待ち行列から始める。** 引き渡しが成立していることをここで確かめる
  await page.getByRole('link', { name: '経路設計' }).click();
  await page.getByRole('row', { name: /JPOSA/ }).first()
    .getByRole('link', { name: '経路を割り当て' }).click();

  await page.getByRole('button', { name: '経路候補を算出する' }).click();
  // **算出は PRG である。** 戻ってきた画面で候補が出るまで待たずに押すと、
  // 押した先が古い画面になる
  await expect(page.getByRole('button', { name: 'この経路で確定' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'この経路で確定' }).first().click();
  await page.waitForURL(/\/bookings\/[0-9a-f-]+$/);
  await expectStatusBadge(page, '割り当て済');

  // ---- 営業担当者: 確定した経路を荷主に通知する（US12） ----
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await page.getByRole('link', { name: '荷主に経路を通知' }).click();
  await expect(page.getByRole('heading', { name: '荷主への経路通知' })).toBeVisible();
  await page.getByRole('button', { name: 'この内容で通知する' }).click();

  // **「送ったつもり」を検知できることが US12 の目的である。**
  // 送信操作が成功したことではなく、**記録が残ったこと**を確かめる
  await expect(page.locator('.alert-success')).toContainText('荷主に経路を通知しました');
  await expect(page.getByRole('heading', { name: '通知履歴' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '経路確定' }).first()).toBeVisible();

  // ---- 営業担当者: 予約を確定する ----
  await page.getByRole('button', { name: '予約を確定' }).click();
  await expectStatusBadge(page, '確認済');

  // ---- 追跡管理者: 追跡番号を発行する ----
  await loginAs(page, USERS.tracker);
  // **待ち行列から始める。** 通知が無くても発行の依頼が届いていることを確かめる
  await page.getByRole('link', { name: '追跡管理' }).click();
  await page.getByRole('link', { name: '予約を開いて発行' }).first().click();
  await page.getByRole('button', { name: '追跡番号を発行' }).click();
  await expectStatusBadge(page, '追跡番号発行済');

  const trackingNumber = await page.locator('code', { hasText: /^TRK-/ }).first().innerText();
  expect(trackingNumber).toMatch(/^TRK-\d{8}-\d{4}$/);

  // **航海番号を決め打ちにしない。** 実際に確定した便と違う番号で積み込むと、
  // それは誤配であり、意図した経路を通っていないまま「輸送中」だけが緑になる
  const voyageNumber = await page.locator('table code').first().innerText();

  // ---- 荷役作業員: 積込を記録する ----
  await loginAs(page, USERS.handler);
  await page.getByRole('link', { name: '荷役管理' }).click();
  await page.getByRole('link', { name: '新規登録', exact: true }).click();

  await page.fill('#trackingNumber', trackingNumber);
  await page.selectOption('#type', 'LOAD');
  await page.fill('#completionTime', localDateTime());
  await page.fill('#locationUnlocode', 'JPOSA');
  await page.fill('#voyageNumber', voyageNumber);
  await page.getByRole('button', { name: '登録する' }).click();

  // 登録した作業が先頭に出る（自分が今スキャンした荷物を探し直させない）
  await expect(page.getByRole('cell', { name: '積込' }).first()).toBeVisible();
  // **予定どおりの積込であることを確かめる。** 誤配でも「輸送中」にはなるため、
  // 状態だけを見ると意図した経路を通ったかどうかを区別できない
  await expect(page.locator('.alert-warning')).toHaveCount(0);

  // ---- 追跡管理者: 出港を手で入れる（US17） ----
  // **出港は荷役作業員が登録しない。** 船が出たことは荷役の記録に現れず、
  // 手で入れる以外に追跡へ反映する手段が無い
  await loginAs(page, USERS.tracker);
  await page.getByRole('link', { name: '追跡管理' }).click();
  // **作業入口から対象へ行けることを確かめる。** 追跡番号を覚えている担当者はいない
  await page.getByRole('row', { name: new RegExp(trackingNumber) })
    .getByRole('link', { name: '状態を確認・更新' }).click();

  await page.selectOption('#eventType', 'DEPART');
  await page.fill('#location', 'JPOSA');
  await page.fill('#occurredAt', localDateTime());
  await page.getByRole('button', { name: '更新する' }).click();

  await expectStatusBadge(page, '搭載中');
  // **手で入れたことが履歴に残る。** 現場の記録と重みが違う
  await expect(page.getByRole('cell', { name: '手動 tracker' })).toBeVisible();

  // ---- 営業担当者: 輸送が始まっている。荷受人を登録する ----
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expectStatusBadge(page, '輸送中');

  // **引取までに荷受人を入れる。** 引取時の本人確認に使う（US16）
  await page.fill('#consigneeName', '受取花子');
  await page.getByRole('button', { name: /荷受人を(登録|訂正)/ }).click();
  await expect(page.locator('.alert-success')).toContainText('荷受人を登録しました');

  // ---- 荷役作業員: 荷降しと引取を記録する ----
  await loginAs(page, USERS.handler);
  await registerHandling(page, {
    trackingNumber,
    type: 'UNLOAD',
    location: 'USLAX',
    voyageNumber,
  });
  // **予定どおりの荷降しであることを確かめる**（誤配でも記録は残るため）
  await expect(page.locator('.alert-warning')).toHaveCount(0);

  // **国をまたぐ輸送には通関が要る**（US29 / C29）。通関を通していない貨物は
  // 引取が拒まれる。**申告を出し忘れている貨物こそ引き取らせてはいけない**
  await registerHandling(page, {
    trackingNumber,
    type: 'CUSTOMS',
    location: 'USLAX',
  });
  const declarationNumber = `DEC-CP-${Date.now()}`;
  await page.goto('/handling/customs/new');
  await page.fill('#trackingNumber', trackingNumber);
  await page.fill('#declarationNumber', declarationNumber);
  await page.fill('#declaredAt', localDateTime());
  await page.getByRole('button', { name: '申告を登録する' }).click();
  await page.getByRole('link', { name: declarationNumber }).click();
  await page.selectOption('#status', 'CLEARED');
  await page.fill('#reason', '通関が完了しました');
  await page.getByRole('button', { name: '状態を更新する' }).click();

  // **引取確認コードは予約詳細から読む**（US35）。IT7 までは任意の値を
  // 書き写すだけだったが、いまは採番済みのコードと照合される。
  // **追跡番号を入れても引き取れない**（別の値である）
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  const claimCode = await page.locator('code', { hasText: /^CLM-/ }).first().innerText();

  await loginAs(page, USERS.handler);
  await registerHandling(page, {
    trackingNumber,
    type: 'CLAIM',
    location: 'USLAX',
    confirmationCode: claimCode,
    consigneeName: '受取花子',
  });
  await expect(page.getByRole('cell', { name: '引取' }).first()).toBeVisible();
  await expect(page.locator('.alert-warning')).toHaveCount(0);

  // ---- 営業担当者: 配送が完了している ----
  await loginAs(page, USERS.sales);
  await page.goto(detailUrl);
  await expectStatusBadge(page, '配送完了');
  // **引き渡し済み以降は荷受人を変えられない**（誰に渡したかの記録を守る）
  await expect(page.getByText('引き渡し済みのため、荷受人は変更できません')).toBeVisible();

  // ---- 荷主: 追跡番号で状況を照会する（US18） ----
  await loginAs(page, USERS.shipper);
  await page.getByRole('link', { name: '貨物追跡' }).click();
  await page.fill('#trackingNumber', trackingNumber);
  await page.getByRole('button', { name: '追跡する' }).click();

  await expect(page.getByRole('heading', { name: '追跡詳細' })).toBeVisible();
  await expectStatusBadge(page, '引取完了');
  // **通った道が履歴に残っている。** 状態だけでは意図した経路を通ったか分からない
  await expect(page.getByRole('cell', { name: '積込' }).first()).toBeVisible();
  await expect(page.getByRole('cell', { name: '荷降し' }).first()).toBeVisible();
  await expect(page.getByRole('cell', { name: '引取' }).first()).toBeVisible();
});
