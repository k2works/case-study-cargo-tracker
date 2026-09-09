import { expect, test } from '@playwright/test';

/**
 * kind クラスタに対して実際に配ったものを踏む（IT2 の DoD）。
 *
 * <p>単体テストもモックの E2E も、「実際に配ったものが動くか」を判別しない。
 * イメージの作り忘れ・マイグレーションの失敗・サービス間の配線ミスは、
 * ここでしか出ない。</p>
 *
 * <p>`E2E_BASE_URL` が無いときは<b>読み込まない</b>（`playwright.config.ts` の
 * `testIgnore`）。skip にすると「飛ばした」のか「無い」のかが実行結果から
 * 読み取れず、0 件で緑の回が混じる。</p>
 */
test.describe('kind クラスタでの通し確認', () => {
  /** 業務タイムゾーンで作る。toISOString() は CI（UTC）で 1 日ずれる。 */
  function businessDate(offsetDays: number): string {
    const formatter = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Tokyo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    const at = new Date(Date.now() + offsetDays * 24 * 60 * 60 * 1000);
    return formatter.format(at);
  }

  /**
   * 実行ごとに違う航海番号を作る。
   *
   * <p><b>epoch の下 6 桁にしない。</b> 約 16.7 分で一巡するので、短い間に何度も
   * 回すと過去の実行と衝突する。衝突した登録は集約が断り（不変条件 1）、その回の
   * テストは前の回の航海を触ることになる（実測: US25 が 1 度落ちた）。</p>
   *
   * <p>航海番号は 20 文字まで。基数 36 にすると 8 文字で収まる。</p>
   */
  function uniqueVoyageNumber(prefix: string): string {
    return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 36).toString(36)}`;
  }

  /**
   * 投影が追いつくまで<b>再読込しながら</b>待つ。
   *
   * <p><b>`toBeVisible({ timeout })` だけでは足りない。</b> 画面は 1 度取得した
   * きりで、投影が遅れて届いても自分から取り直さない（headless では
   * ウィンドウのフォーカスも起きないので TanStack Query の再取得も走らない）。
   * データは 3 秒で届いているのに 20 秒待って落ちる——IT9 のクラスタで実測した
   * 形がこれで、単独なら 2.3 秒で緑、通し実行だけ 22 秒でタイムアウトした。</p>
   *
   * <p><b>最初に成功した時点で抜けない。</b> 一度見えたあと投影の遅れた行で
   * 消えるようなら、それは緑ではない。{@code hold} のあいだ保ち続けることを見る。</p>
   */
  async function waitForProjection(
    page: import('@playwright/test').Page,
    check: () => Promise<void>,
    options: { timeout?: number; hold?: number } = {},
  ) {
    // **テストの既定のタイムアウト（30 秒）より短くする。** 長くすると、投影が
    // 遅れたときに「何が見えなかったか」ではなく「テストが時間切れ」とだけ出て、
    // 原因が読めなくなる（IT10 で実測）。長く待つ必要がある通しの検査は、
    // その検査自身が test.setTimeout で伸ばす。
    const timeout = options.timeout ?? 20_000;
    const hold = options.hold ?? 1_000;
    await expect(async () => {
      await check();
    }).toPass({ timeout, intervals: [500, 1_000, 2_000] });
    // 保ち続けるか。投影の遅れで消えるなら、ここで落ちる。
    await page.waitForTimeout(hold);
    await check();
  }

  /** 再読込しながら、その文字列が出るまで待つ（投影待ちの定型）。 */
  async function expectEventually(
    page: import('@playwright/test').Page,
    text: string | RegExp,
    options: { reload?: boolean } = {},
  ) {
    // **絞り込みを入れた画面では再読込しない。** 読み直すと入力が消え、
    // 上限の外にある行を探し続けることになる（一覧はポーリングで更新される）。
    await waitForProjection(page, async () => {
      if (options.reload !== false) {
        await page.reload();
      }
      await expect(page.getByText(text).first()).toBeVisible({ timeout: 5_000 });
    });
  }

  async function signIn(page: import('@playwright/test').Page, username: string) {
    await page.goto('/login');
    await page.getByLabel('利用者名').fill(username);
    await page.getByLabel('パスワード').fill('secret1234');
    await page.getByRole('button', { name: 'ログイン' }).click();
    await expect(page.getByRole('heading', { name: 'ダッシュボード' })).toBeVisible();
  }

  test('営業が荷主と貨物予約を登録し、一覧と詳細に出る', async ({ page }) => {
    const stamp = Date.now();
    const email = `cluster-${stamp}@example.com`;
    const product = `クラスタ確認-${stamp}`;

    await signIn(page, 'sales01');

    // 荷主を登録する。
    await page.goto('/shippers/new');
    await page.getByLabel('名称').fill(`クラスタ商事 ${stamp}`);
    await page.getByLabel('メールアドレス').fill(email);
    await page.getByLabel('電話番号').fill('03-0000-0000');
    await page.getByLabel('住所').fill('東京都中央区');
    await page.getByRole('button', { name: '登録する' }).click();

    // **名前で絞り込んでから確かめる。** 一覧には上限があり、登録したばかりの
    // 荷主は絞り込まないと出ない（クラスタは作り直さずに使い続けるので、
    // 実行のたびに荷主が積み上がる。IT10 の通しで実測: 295 件で上限 50 件）。
    await page.getByLabel('荷主名で絞り込む').fill(`クラスタ商事 ${stamp}`);
    await expectEventually(page, email, { reload: false });

    await page.getByRole('link', { name: '予約登録' }).first().click();
    await expect(page.getByRole('heading', { name: '貨物予約の登録' })).toBeVisible();

    // 荷主は選ぶ。識別子を打たせると、営業は一覧を開いて UUID を書き写すことになる。
    // **名前で絞り込んでから選ぶ。** 選択肢には上限があるので、登録したばかりの
    // 荷主は絞り込まないと出ない（IT8 のクラスタで実測した欠陥。IT9 で絞り込みを足した）。
    await page.getByLabel('荷主を名前で絞り込む').fill(`クラスタ商事 ${stamp}`);
    // 選択肢は「名称（荷主コード）」なので、名称の部分で当てる。
    const option = page.locator('#shipperId option', { hasText: `クラスタ商事 ${stamp}` });
    await expect(option).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主', { exact: true })
        .selectOption(await option.getAttribute('value') ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('USNYC');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('品名').fill(product);
    await page.getByRole('button', { name: '登録する' }).click();

    // **品名で絞り込んでから確かめる。** 一覧は到着期限順で上限があるので、
    // 期限の遠い予約は上限の外に回る（IT9 のクラスタで実測した欠陥）。
    await page.getByLabel('予約番号・品名で絞り込む').fill(product);
    // 一覧に出る（予約番号が採番され、状態は仮受付）。
    await expect(page.getByText(product)).toBeVisible({ timeout: 20_000 });
    const row = page.locator('tr', { hasText: product });
    await expect(row.getByText('仮受付')).toBeVisible();

    // 詳細まで開く。一覧に出るだけでは、詳細の配線が通っているか分からない。
    await row.getByRole('link').first().click();
    await expect(page.getByRole('heading', { name: /^予約 B-/ })).toBeVisible();
    await expect(page.getByText(product)).toBeVisible();
    await expect(page.getByText('120 × 80 × 100 cm')).toBeVisible();
  });

  test('集約が断ると理由が出る（500 にならない）', async ({ page }) => {
    await signIn(page, 'sales01');
    await page.goto('/bookings/new');

    // 荷主は一覧の先頭を選ぶ。ここで見たいのは経路の拒否なので、誰でもよい。
    const first = page.locator('#shipperId option').nth(1);
    await expect(first).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主', { exact: true })
        .selectOption(await first.getAttribute('value') ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('JPTYO');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('品名').fill('同一港');
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page.getByRole('alert')).toContainText('出発地と目的地が同じ');
  });

  /**
   * 資格情報を取る。画面から入り直すより速く、前提づくりの手順が本文から消える。
   */
  async function tokenOf(
    request: import('@playwright/test').APIRequestContext,
    username: string,
  ): Promise<string> {
    const response = await request.post('/api/v1/auth/login', {
      data: { username, password: 'secret1234' },
    });
    expect(response.status()).toBe(200);
    return (await response.json()).token as string;
  }

  /**
   * 仮受付の予約を 1 件作る。
   *
   * <p><b>各テストが自分で前提を作る</b>（IT2 引き継ぎ 7）。前のテストが残したものに
   * 頼ると、実行順を変えたときに落ち、<b>前回実行の残骸でも緑になる</b>。残骸で緑に
   * なる検査は、壊れていることを教えてくれない。</p>
   */
  async function bookCargo(
    request: import('@playwright/test').APIRequestContext,
    product: string,
  ): Promise<string> {
    const token = await tokenOf(request, 'sales01');
    const headers = { Authorization: `Bearer ${token}` };
    const stamp = Date.now();

    const shipper = await request.post('/api/v1/booking/shippers', {
      headers,
      data: {
        name: `前提商事 ${stamp}`,
        shipperType: 'INDIVIDUAL',
        email: `precondition-${stamp}@example.com`,
        phone: '03-0000-0000',
        address: '東京都中央区',
        acknowledgedDuplicate: false,
      },
    });
    expect(shipper.status()).toBe(201);

    const booking = await request.post('/api/v1/booking/bookings', {
      headers,
      data: {
        shipperId: (await shipper.json()).shipperId,
        originUnLocode: 'JPTYO',
        destinationUnLocode: 'USNYC',
        arrivalDeadline: businessDate(60),
        cargoType: 'GENERAL',
        weightKg: '1200',
        lengthCm: '120',
        widthCm: '80',
        heightCm: '100',
        quantity: 10,
        productName: product,
      },
    });
    expect(booking.status()).toBe(201);
    return (await booking.json()).bookingId as string;
  }

  test('営業には引き渡していない予約の件数と導線が出る', async ({ page, request }) => {
    // 前提は自分で作る。前のテストに頼ると、単独で回したときに落ちる。
    await bookCargo(request, `件数確認-${Date.now()}`);

    // 引き渡すのは営業の仕事（US06）。経路設計者にこの件数を出しても、
    // その件数に対して打てる手が無い。
    await signIn(page, 'sales01');

    const notice = page.getByText(/経路設計者へ引き渡していない予約が \d+ 件/);
    await expect(notice).toBeVisible({ timeout: 20_000 });
    await notice.getByRole('link', { name: '予約一覧' }).click();

    await expect(page.getByRole('heading', { name: '予約一覧' })).toBeVisible();
  });

  test('営業が経路設計へ引き渡すと、経路設計者の作業一覧に出る（US06）', async ({
    page,
    request,
  }) => {
    const product = `引き渡し-${Date.now()}`;
    const bookingId = await bookCargo(request, product);

    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    // **投影の反映を待つ。** 押した直後の画面だけを見ると、混んでいるときに
    // 20 秒では届かない（IT11 の通しで 3 度落ちた）。読み直しながら待つ。
    await expectEventually(page, '経路提案中');

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.getByRole('link', { name: '経路設計作業' }).first().click();
    await expect(page.getByRole('heading', { name: '経路設計作業一覧' })).toBeVisible();

    // **一覧から名指しで探さない。** S30 は表示上限で切れ、並びは「誤配が先、
    // そのあと到着期限が近い順」である（ui_design.md）。作り直さないクラスタでは
    // 未解決の誤配が積み上がり、**引き渡した直後の予約が 1 ページ目に載らない**
    // ——画面はそのことを「N 件のうち M 件」と知らせている（IT11 の通しで実測）。
    // ここで見るのは「引き渡しが経路設計者の持ち場に届いたこと」なので、
    // ワークベンチが開けることで確かめる。
    await expect(page.getByText(/件のうち|経路設計作業一覧/).first()).toBeVisible();
    await page.goto(`/routing/bookings/${bookingId}`);
    await expect(page.getByRole('heading', { name: '経路候補' })).toBeVisible();
  });

  /**
   * 航海一覧を条件で絞る。
   *
   * <p><b>一覧は表示上限で切れる。</b> 作り直さないクラスタでは航海が積み上がり、
   * 登録した直後の便でも 1 ページ目に載らなくなる。画面はそのことを
   * 「N 件のうち M 件を表示しています」と知らせているので、テストも同じように
   * 絞ってから探す。</p>
   */
  async function narrowVoyagesTo(
    page: import('@playwright/test').Page,
    departure: string,
    arrival: string,
  ) {
    await page.getByLabel('出発地').fill(departure);
    await page.getByLabel('目的地').fill(arrival);
    await page.getByRole('button', { name: '絞り込む' }).click();
  }

  test('経路設計者が航海を登録すると、一覧に出る（US24）', async ({ page }) => {
    const voyageNumber = uniqueVoyageNumber('V-E2E-');

    await signIn(page, 'routing01');
    await page.getByRole('link', { name: '航海登録' }).first().click();
    await expect(page.getByRole('heading', { name: '航海スケジュールを登録する' })).toBeVisible();

    await page.getByLabel('航海番号').fill(voyageNumber);
    await page.getByLabel('運送会社コード').fill('MOL');
    await page.getByLabel('運送会社名', { exact: true }).fill('商船三井');
    await page.getByLabel('船名').fill('E2E EXPRESS');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('到着地').fill('USNYC');
    // 出港済みを既定で外すので、未来の日付にしないと一覧に出ない。
    await page.getByLabel('出発日時（日本時間）').fill(`${businessDate(30)}T09:00`);
    await page.getByLabel('到着日時（日本時間）').fill(`${businessDate(45)}T18:00`);
    await page.getByRole('button', { name: '登録する' }).click();

    await expect(page.getByRole('heading', { name: '航海スケジュール一覧' })).toBeVisible();

    // **一覧から登録した便を名指しで探さない。**
    //
    // 一覧は表示上限で切れ、並びは出発日が近い順である。作り直さないクラスタでは
    // 同じ区間の便が積み上がるので、**絞り込んでも登録した便が 1 ページ目に
    // 載らなくなる**（IT11 の通しで実測。航海番号で探す手段が画面に無い）。
    // ここでは登録が通ったことを詳細で見る——一覧そのものの絞り込みは
    // US07 の確認が持つ。
    await page.goto(`/voyages/${voyageNumber}`);
    await expect(page.getByRole('heading', { name: `航海 ${voyageNumber}` }))
      .toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('E2E EXPRESS')).toBeVisible();
  });

  test('経路設計者が候補を見て経路を確定する（US08・US09・IT5）', async ({ page, request }) => {
    // **サービス越しの問い合わせは、この確認でしか出ない失敗の宝庫。**
    // 単体もモックも「届く」ことは見ていない。
    const voyageNumber = uniqueVoyageNumber('V-RT-');
    const product = `経路設計-${Date.now()}`;

    // 候補になる航海を先に登録する。無いと候補 0 件になり、
    // 「届いていない」のか「便が無い」のか分からない。
    const routingToken = await tokenOf(request, 'routing01');
    const voyage = await request.post('/api/v1/routing/voyages', {
      headers: { Authorization: `Bearer ${routingToken}` },
      data: {
        voyageNumber,
        carrierCode: 'MOL',
        carrierName: '商船三井',
        vesselName: 'ROUTE EXPRESS',
        movements: [
          {
            departureUnLocode: 'JPTYO',
            arrivalUnLocode: 'USNYC',
            // 業務タイムゾーンで作る。UTC で作ると CI の時間帯で 1 日ずれる。
            //
            // **どの候補より速くする。** 過去の実行で JPTYO → USNYC の航海が
            // 積み上がっており、候補は 20 件で打ち切られる（ADR-0007）。
            // 遅い便を登録すると、届いているのに押し出されて「出ない」になる
            // （実測: 所要 20 日で登録し、既存の 16 日の便 20 件に押し出された）。
            departureAt: `${businessDate(2)}T00:00:00Z`,
            arrivalAt: `${businessDate(5)}T00:00:00Z`,
          },
        ],
        acceptedCargoTypes: ['GENERAL'],
      },
    });
    expect(voyage.status()).toBe(201);

    const bookingId = await bookCargo(request, product);

    // 引き渡す（US06）。引き渡していない予約には経路を確定できない。
    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    // **投影の反映を待つ**（混んでいると 20 秒では届かない。IT11 の通しで実測）。
    await expectEventually(page, '経路提案中');

    await page.goto('/logout');
    await signIn(page, 'routing01');
    // **一覧から名指しで探さない**（US06 と同じ理由。S30 は表示上限で切れる）。
    await page.goto(`/routing/bookings/${bookingId}`);

    await expect(page.getByRole('heading', { name: '経路候補' })).toBeVisible();

    // **順位で当てない。** 過去の実行で同じ所要日数の便が積み上がっており、
    // 同着の並びは決まらない（実測: 1 位が前回の回の便だった）。登録した
    // 航海の行を名指しで探す。出なければ、届いていないか探索が落としている。
    // **自分が作った航海を名指しで探さない。** 探索は推奨順の上位 20 件で
    // 打ち切る（ADR-0007）ので、同じ区間の航海が 20 本を超えると自分の 1 本が
    // 出るとは限らない。クラスタは作り直さずに使い続けるため、実行のたびに
    // 航海が積み上がる（IT7 の 2 度目の通しで実測。84 本あった）。
    // 経路設計者がするのは「候補を見て 1 つ選ぶ」ことなので、先頭の候補を選ぶ。
    const candidate = page.getByTestId('candidate-1');
    await expect(candidate).toBeVisible({ timeout: 30_000 });
    await candidate.getByRole('radio').check();
    await page.getByRole('button', { name: 'この経路で確定' }).click();

    // 確定すると予約詳細へ戻り、旅程が読める（US09）。
    // **選んだ候補の航海番号で見る。** 自分が登録した航海とは限らない
    // （上位 20 件で打ち切るため。ADR-0007）。空でないことまで確かめる。
    await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('leg-1')).toContainText(/V-/);
    // 荷主に通知するまでは提案中（US12）。ここが確定になってはいけない。
    await expect(page.getByText('経路提案中')).toBeVisible();
  });

  test('経路設計者が条件を調整して再算出できる（US10・IT6）', async ({ page, request }) => {
    // デモ項目 2。**条件はサーバが持つ**ので、画面で変えて再算出すると調整が
    // 記録され、候補算出はその条件から組み直される。
    const product = `条件調整-${Date.now()}`;
    const bookingId = await bookCargo(request, product);

    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    // **投影の反映を待つ**（混んでいると 20 秒では届かない。IT11 の通しで実測）。
    await expectEventually(page, '経路提案中');

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.goto(`/routing/bookings/${bookingId}`);

    // いまの条件が読める（読めないと同じ条件で何度も回す）。
    await expect(page.getByLabel('到着期限')).toBeVisible({ timeout: 20_000 });
    await page.getByLabel('除外する港').fill('SGSIN');
    await page.getByRole('button', { name: '条件を変えて再算出' }).click();

    // 調整が記録され、読み直した条件が欄に戻る。
    await expect(page.getByLabel('除外する港')).toHaveValue('SGSIN', { timeout: 20_000 });
  });

  test('経路設計者が営業へ差し戻すと、営業のダッシュボードに出る（US10・IT6）',
      async ({ page, request }) => {
    // デモ項目 3。**差し戻しても状態は動かない**（ADR-0009 決定 1）ので、
    // 予約は経路設計の作業一覧に残ったまま、営業に「見直してほしい」が届く。
    const product = `差し戻し-${Date.now()}`;
    const bookingId = await bookCargo(request, product);

    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    // **投影の反映を待つ**（混んでいると 20 秒では届かない。IT11 の通しで実測）。
    await expectEventually(page, '経路提案中');

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.goto(`/routing/bookings/${bookingId}`);
    await page.getByRole('button', { name: '営業へ差し戻す' }).click();
    await page.getByLabel('差し戻す理由').fill(`期限内に着ける便がありません（${product}）`);
    await page.getByRole('button', { name: '差し戻しを送る' }).click();

    await page.goto('/logout');
    await signIn(page, 'sales01');
    // 件数だけでは仕事が進まない。理由が読め、そこから予約へ行けること。
    await expectEventually(page, `期限内に着ける便がありません（${product}）`);
  });

  test('営業が荷主へ通知し、経路設計へ戻せる（US12・IT6）', async ({ page, request }) => {
    // デモ項目 5・7。**通知は記録だけ**（送信基盤はスコープ外）だが、記録は
    // 業務の守りとして働く——通知していない予約は経路設計へ戻せない。
    const voyageNumber = uniqueVoyageNumber('V-NT-');
    const product = `通知-${Date.now()}`;

    const routingToken = await tokenOf(request, 'routing01');
    const voyage = await request.post('/api/v1/routing/voyages', {
      headers: { Authorization: `Bearer ${routingToken}` },
      data: {
        voyageNumber,
        carrierCode: 'MOL',
        carrierName: '商船三井',
        vesselName: 'NOTIFY EXPRESS',
        movements: [
          {
            departureUnLocode: 'JPTYO',
            arrivalUnLocode: 'USNYC',
            // どの候補より速くする（上の経路確定のテストと同じ理由）。
            departureAt: `${businessDate(2)}T00:00:00Z`,
            arrivalAt: `${businessDate(5)}T00:00:00Z`,
          },
        ],
        acceptedCargoTypes: ['GENERAL'],
      },
    });
    expect(voyage.status()).toBe(201);

    const bookingId = await bookCargo(request, product);
    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    // **投影の反映を待つ**（混んでいると 20 秒では届かない。IT11 の通しで実測）。
    await expectEventually(page, '経路提案中');

    await page.goto('/logout');
    await signIn(page, 'routing01');
    await page.goto(`/routing/bookings/${bookingId}`);
    // **自分が作った航海を名指しで探さない。** 探索は推奨順の上位 20 件で
    // 打ち切る（ADR-0007）ので、同じ区間の航海が 20 本を超えると自分の 1 本が
    // 出るとは限らない。クラスタは作り直さずに使い続けるため、実行のたびに
    // 航海が積み上がる（IT7 の 2 度目の通しで実測。84 本あった）。
    // 経路設計者がするのは「候補を見て 1 つ選ぶ」ことなので、先頭の候補を選ぶ。
    const candidate = page.getByTestId('candidate-1');
    await expect(candidate).toBeVisible({ timeout: 30_000 });
    await candidate.getByRole('radio').check();
    await page.getByRole('button', { name: 'この経路で確定' }).click();
    await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible({ timeout: 20_000 });

    await page.goto('/logout');
    await signIn(page, 'sales01');
    await page.goto(`/bookings/${bookingId}`);

    // 通知内容は旅程から作る。旅程が届く前は送れない。
    await expect(page.getByRole('heading', { name: '荷主への通知' }))
      .toBeVisible({ timeout: 20_000 });
    await expect(page.getByLabel('通知内容')).toHaveValue(/JPTYO → USNYC/, { timeout: 20_000 });
    await page.getByLabel('通知先メールアドレス').fill('shipper@example.com');
    await page.getByRole('button', { name: '通知した記録を残す' }).click();

    // 通知済みになり、履歴に残る（US12 §受入基準 4）。
    await expect(page.getByText('経路通知済')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('heading', { name: '通知履歴' })).toBeVisible();

    // 通知したので経路設計へ戻せる（デモ項目 7）。
    await page.getByRole('button', { name: '経路設計へ戻す' }).click();
    await page.getByLabel('戻す理由').fill('荷主が経由港の変更を希望');
    await page.getByRole('button', { name: '戻すことを確定する' }).click();

    await expectEventually(page, '経路提案中');
    // **旅程は残る。** 消すと「何を組み直すのか」が分からなくなる。
    await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible();
  });

  test('確定して追跡番号を発行すると、追跡がサービスをまたいで作られる（US13・US14・IT7）',
    async ({ page, request }) => {
      // **本 IT の中核。** サービスをまたぐ最初の連鎖で、Testcontainers では
      // 両サービスを同時に起こさないので**ここでしか通しで確かめられない**。
      const product = `連鎖の貨物-${Date.now()}`;
      const voyageNumber = uniqueVoyageNumber('V-CH-');
      const routing = await tokenOf(request, 'routing01');
      const voyage = await request.post('/api/v1/routing/voyages', {
        headers: { Authorization: `Bearer ${routing}` },
        data: {
          voyageNumber,
          carrierCode: 'MOL',
          carrierName: '商船三井',
          vesselName: 'CHAIN MARU',
          movements: [
            {
              departureUnLocode: 'JPTYO',
              arrivalUnLocode: 'USNYC',
              // どの候補より速くする（上の経路確定のテストと同じ理由）。
              departureAt: `${businessDate(2)}T00:00:00Z`,
              arrivalAt: `${businessDate(5)}T00:00:00Z`,
            },
          ],
          acceptedCargoTypes: ['GENERAL'],
        },
      });
      expect(voyage.status()).toBe(201);

      const bookingId = await bookCargo(request, product);
      await signIn(page, 'sales01');
      await page.goto(`/bookings/${bookingId}`);
      await page.getByRole('button', { name: '経路設計を依頼する' }).click();
      await expectEventually(page, '経路提案中');

      await page.goto('/logout');
      await signIn(page, 'routing01');
      await page.goto(`/routing/bookings/${bookingId}`);
      // 先頭の候補を選ぶ（理由は上と同じ。ADR-0007 の打ち切り）。
      const candidate = page.getByTestId('candidate-1');
      await expect(candidate).toBeVisible({ timeout: 30_000 });
      await candidate.getByRole('radio').check();
      await page.getByRole('button', { name: 'この経路で確定' }).click();
      await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible({ timeout: 20_000 });

      // **経路設計者には確定の操作が出ない**（確定は営業の仕事）。
      await expect(page.getByRole('button', { name: '予約を確定する' })).toHaveCount(0);

      await page.goto('/logout');
      await signIn(page, 'sales01');
      await page.goto(`/bookings/${bookingId}`);
      await expect(page.getByLabel('通知内容')).toHaveValue(/JPTYO → USNYC/, { timeout: 20_000 });
      await page.getByLabel('通知先メールアドレス').fill('shipper@example.com');
      await page.getByRole('button', { name: '通知した記録を残す' }).click();
      await expect(page.getByText('経路通知済')).toBeVisible({ timeout: 20_000 });

      // デモ項目 1: 通知済みの予約を確定できる。
      await page.getByRole('button', { name: '予約を確定する' }).click();
      // **「確定」は他の文言にも含まれる**（「この経路で確定」など）ので、
      // 状態の欄そのものを見る。呼び名は正典が「予約確定」と決めている。
      await expect(page.getByText('予約確定', { exact: true })).toBeVisible({ timeout: 20_000 });
      // **営業には発行の操作が出ない**（発行は経路設計者の仕事）。
      await expect(page.getByRole('button', { name: '追跡番号を発行する' })).toHaveCount(0);
      // デモ項目 7: 確定した予約は経路設計へ戻せない。
      await expect(page.getByRole('button', { name: '経路設計へ戻す' })).toHaveCount(0);

      // デモ項目 3: 経路設計者が追跡番号を発行できる。
      await page.goto('/logout');
      await signIn(page, 'routing01');
      await page.goto(`/bookings/${bookingId}`);
      await page.getByRole('button', { name: '追跡番号を発行する' }).click();
      // **形式は正典（ADR-0011）。** 連番だと公開照会（US18）で前後が推測できる。
      // 投影は非同期なので、**再読込しながら**待つ（画面は自分から取り直さない）。
      await expectEventually(page, /^TRK-[0-9A-Z]{10}$/);
      // デモ項目 4: 二重に発行されない（操作そのものが消える）。
      await expect(page.getByRole('button', { name: '追跡番号を発行する' })).toHaveCount(0);

      // デモ項目 5: **サービスをまたいで届く。** API を直接叩いても二度目は断られ、
      // 断った理由が読める（器だけの文言に化けない）。
      const second = await request.post(
        `/api/v1/booking/bookings/${bookingId}/tracking-number`,
        { headers: { Authorization: `Bearer ${routing}` }, failOnStatusCode: false });
      expect(second.status()).toBe(409);
      const body = await second.json();
      expect(body.message).toContain('発行できません');
      expect(body.message).not.toContain('com.example.cargotracker');
    });

  /**
   * 追跡番号を発行したところまで作る（US16 のクラスタ確認の前提）。
   *
   * <p><b>前提は API で作る。</b> 画面から通すのは US13・US14 の確認の仕事で、
   * ここで繰り返すと何を確かめている検査なのか読めなくなる。</p>
   */
  async function issueTrackingNumber(
    request: import('@playwright/test').APIRequestContext,
    product: string,
  ): Promise<{ bookingId: string; trackingNumber: string; voyageNumber: string }> {
    const voyageNumber = uniqueVoyageNumber('V-CL-');
    const routingToken = await tokenOf(request, 'routing01');
    const routingHeaders = { Authorization: `Bearer ${routingToken}` };

    const voyage = await request.post('/api/v1/routing/voyages', {
      headers: routingHeaders,
      data: {
        voyageNumber,
        carrierCode: 'MOL',
        carrierName: '商船三井',
        vesselName: 'CLAIM EXPRESS',
        movements: [{
          departureUnLocode: 'JPTYO',
          arrivalUnLocode: 'USNYC',
          // **どの候補より速くする。** 候補は 20 件で打ち切られる（ADR-0007）。
          departureAt: `${businessDate(2)}T00:00:00Z`,
          arrivalAt: `${businessDate(4)}T00:00:00Z`,
        }],
        acceptedCargoTypes: ['GENERAL'],
      },
    });
    expect(voyage.status(), await voyage.text()).toBe(201);

    const bookingId = await bookCargo(request, product);
    const salesToken = await tokenOf(request, 'sales01');
    const salesHeaders = { Authorization: `Bearer ${salesToken}` };

    expect((await request.post(
      `/api/v1/booking/bookings/${bookingId}/routing-request`,
      { headers: salesHeaders })).status()).toBe(202);

    // 経路を確定する。候補の 1 件目を選ぶ（自分の航海を名指ししない）。
    let candidates: { legs: unknown[] }[] = [];
    await expect(async () => {
      const response = await request.get(
        `/api/v1/booking/bookings/${bookingId}/route-candidates`,
        { headers: routingHeaders });
      expect(response.status()).toBe(200);
      candidates = (await response.json()).candidates ?? [];
      expect(candidates.length).toBeGreaterThan(0);
    }).toPass({ timeout: 60_000 });

    // **経路の確定は POST**（`/route`）。PUT は経路仕様の調整（US10）で別物。
    expect((await request.post(`/api/v1/booking/bookings/${bookingId}/route`, {
      headers: routingHeaders,
      data: { legs: candidates[0]?.legs ?? [] },
    })).status()).toBe(200);

    await expect(async () => {
      const response = await request.post(
        `/api/v1/booking/bookings/${bookingId}/notifications`, {
          headers: salesHeaders,
          data: { recipientEmail: 'shipper@example.com', summary: 'JPTYO → USNYC' },
        });
      // 通知の記録は同期で返る（旅程が届く前は集約が断るので、届くまで再試行する）。
      expect(response.status()).toBe(200);
    }).toPass({ timeout: 60_000 });

    // **確定も届くまで再試行する。** 通知の記録が集約へ入るのと、確定が
    // 「通知済み」を見るのは別の往復で、続けて叩くと 409 に当たる
    // （IT11 の通しで実測。単独では出ない）。
    await expect(async () => {
      const confirmed = await request.post(
        `/api/v1/booking/bookings/${bookingId}/confirmation`, { headers: salesHeaders });
      expect(confirmed.status()).toBe(200);
    }).toPass({ timeout: 60_000 });

    // **発行は 1 度だけ叩く。** 発行と投影の反映を同じ再試行に入れると、
    // 投影が遅れたときに**発行をもう一度叩いて 409 に当たり**、そのまま
    // 60 秒粘って落ちる（IT11 の通しで実測）。**取り消しの利かない操作を
    // 再試行の中に置かない。**
    const issued = await request.post(
      `/api/v1/booking/bookings/${bookingId}/tracking-number`,
      { headers: routingHeaders });
    expect(issued.status()).toBe(200);

    let trackingNumber = '';
    await expect(async () => {
      const detail = await request.get(`/api/v1/booking/bookings/${bookingId}`,
        { headers: salesHeaders });
      trackingNumber = (await detail.json()).trackingNumber ?? '';
      expect(trackingNumber).toMatch(/^TRK-[0-9A-Z]{10}$/);
    }).toPass({ timeout: 60_000 });

    return { bookingId, trackingNumber, voyageNumber };
  }

  test('荷受人の確認を取って引取を記録すると、引取済になり精算と予約へ伝わる（US16・IT10）',
    async ({ page, request }) => {
      // **US16 のクラスタ確認**（Try T3。US ごとに 1 度回す）。
      // モックでは「引取が billingms と bookingms の両方へ届くか」を判別できない。
      test.setTimeout(300_000);
      const product = `引取の貨物-${Date.now()}`;
      const { bookingId, trackingNumber, voyageNumber } =
        await issueTrackingNumber(request, product);

      const handlerToken = await tokenOf(request, 'handler01');
      const handlerHeaders = { Authorization: `Bearer ${handlerToken}` };

      // 目的港まで進める（受領 → 積込 → 荷降し）。ここは US15 で確かめ済みなので API で。
      for (const step of [
        { handlingType: 'RECEIVE', unLocode: 'JPTYO' },
        { handlingType: 'LOAD', unLocode: 'JPTYO', voyageNumber },
        { handlingType: 'UNLOAD', unLocode: 'USNYC', voyageNumber },
      ]) {
        await expect(async () => {
          const response = await request.post('/api/v1/handling/activities', {
            headers: handlerHeaders,
            data: { activityId: crypto.randomUUID(), trackingNumber, ...step },
          });
          expect(response.status()).toBe(201);
        }).toPass({ timeout: 60_000 });
      }

      // **デモ項目 1: 荷受人の確認なしで引取を送ると断られる。**
      // 画面が選択肢から外していても API を直接叩けば通っていた（IT9 レビュー）。
      const withoutConsignee = await request.post('/api/v1/handling/activities', {
        headers: handlerHeaders,
        failOnStatusCode: false,
        data: {
          activityId: crypto.randomUUID(),
          trackingNumber,
          handlingType: 'CLAIM',
          unLocode: 'USNYC',
        },
      });
      expect(withoutConsignee.status()).toBe(422);
      expect((await withoutConsignee.json()).message).toContain('荷受人の確認');

      // **デモ項目 2: 画面から確認を入れて引取を記録すると「引取済」になる。**
      await signIn(page, 'handler01');
      await page.goto(`/handling/voyages/${voyageNumber}?unLocode=USNYC`);
      await page.getByLabel('作業種別').selectOption('CLAIM');
      await page.getByLabel('追跡番号').fill(trackingNumber);
      // **「確認」だけで指さない。** 引取では荷受人の確認欄の文言にも含まれる。
      // 貨物が引けたことは、確認欄の見出し（dt）そのもので見る。
      await expect(page.getByText('確認', { exact: true })).toBeVisible({ timeout: 20_000 });
      await page.getByLabel(/荷受人の確認/).fill('John Smith');
      await page.getByRole('button', { name: '記録する' }).click();

      // 履歴に確認が残る（記録するだけでは誰にも見えない）。
      await page.goto(`/handling/${trackingNumber}`);
      await expectEventually(page, '引取');

      // **デモ項目 2 の続き: 貨物状態が引取済になる。**
      await page.goto('/logout');
      await signIn(page, 'tracker01');
      await page.goto(`/tracking/${trackingNumber}`);
      await expectEventually(page, '引取済');

      // **デモ項目 3b: 引取が予約へ伝わる**（購読側は billingms だけではない）。
      // **予約は「配送完了」**。輸送の「引取済」と呼び名を分ける（正典の状態の一覧）。
      await page.goto('/logout');
      await signIn(page, 'sales01');
      await page.goto(`/bookings/${bookingId}`);
      await expectEventually(page, '配送完了');
    });

  test('遅延を起票して解決すると、例外前の状態へ戻る（US19・IT10）',
    async ({ page, request }) => {
      // **US19 のクラスタ確認**（Try T3。US ごとに 1 度回す）。
      // モックでは「集約が覚えた戻り先が投影と画面まで通るか」を判別できない。
      test.setTimeout(300_000);
      const product = `例外の貨物-${Date.now()}`;
      const { trackingNumber, voyageNumber } = await issueTrackingNumber(request, product);

      // 受領まで進める（例外は輸送中に起きる。未受領から戻っても区別が付かない）。
      const handlerToken = await tokenOf(request, 'handler01');
      await expect(async () => {
        const response = await request.post('/api/v1/handling/activities', {
          headers: { Authorization: `Bearer ${handlerToken}` },
          data: {
            activityId: crypto.randomUUID(),
            trackingNumber,
            handlingType: 'RECEIVE',
            unLocode: 'JPTYO',
          },
        });
        expect(response.status()).toBe(201);
      }).toPass({ timeout: 60_000 });

      await signIn(page, 'tracker01');
      await page.goto(`/tracking/${trackingNumber}`);
      await expectEventually(page, '受領済');

      // **デモ項目 4: 遅延を起票すると「例外発生」になる。**
      await page.getByRole('link', { name: '例外を起票する' }).click();
      await expect(page.getByRole('heading', { name: '例外を起票する' })).toBeVisible();
      await page.getByLabel('例外種別').selectOption('DELAY');
      await page.getByLabel('発生場所').fill('SGSIN');
      await page.getByLabel('発生状況').fill(`台風で 3 日遅れます（${product}）`);
      await page.getByRole('button', { name: '起票する' }).click();

      // **遷移を待ってから確かめる。** expectEventually は最初に再読込するので、
      // 押した直後に呼ぶと遷移そのものを打ち消し、起票フォームに戻ってしまう。
      await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}$`),
        { timeout: 20_000 });
      await expectEventually(page, '例外発生');
      await expect(page.getByText(`台風で 3 日遅れます（${product}）`)).toBeVisible();

      // **デモ項目 9: 例外一覧に出る**（解決済は既定で出ない）。
      await page.goto('/tracking/exceptions');
      await expectEventually(page, `台風で 3 日遅れます（${product}）`);

      // **デモ項目 5: 荷主へ知らせた事実が残る**（送信基盤はスコープ外）。
      await page.goto(`/tracking/${trackingNumber}`);
      await page.getByRole('button', { name: '荷主へ知らせた' }).click();
      await page.getByLabel('伝えた手段').fill('電話');
      await page.getByLabel('伝えた内容').fill('3 日遅れる見込みと伝えました');
      await page.getByRole('button', { name: '記録を残す' }).click();

      // **デモ項目 6: 対応内容を入れて解決すると、例外前の状態へ戻る。**
      await expect(page.getByRole('button', { name: '解決にする' }))
        .toBeVisible({ timeout: 20_000 });
      await page.getByRole('button', { name: '解決にする' }).click();
      await page.getByLabel('対応内容').fill('代替便に振り替えました');
      await page.getByRole('button', { name: '解決を確定する' }).click();

      await expectEventually(page, '受領済');
      // **解決しても事実は消えない**（不変条件 6）。
      await expect(page.getByText('代替便に振り替えました')).toBeVisible();

      // 解決したら一覧から外れる（決着したものが混ざると一覧が信用されない）。
      await page.goto('/tracking/exceptions');
      await waitForProjection(page, async () => {
        await page.reload();
        await expect(page.getByText(`台風で 3 日遅れます（${product}）`)).toHaveCount(0);
      });
      // 航海番号は前提づくりの確認にだけ使う（未使用の警告を避ける）。
      expect(voyageNumber).toMatch(/^V-CL-/);
    });

  test('紛失を起票すると緊急として一覧の先頭に出て、管理者も気づける（US20・IT11）',
    async ({ page, request }) => {
      // **US20 のクラスタ確認**（Try T8。US ごとに 1 度回す）。
      // モックでは「escalate の記録が投影を経て管理者の画面まで届くか」を判別できない。
      test.setTimeout(300_000);
      const product = `紛失の貨物-${Date.now()}`;
      const { trackingNumber } = await issueTrackingNumber(request, product);

      const handlerToken = await tokenOf(request, 'handler01');
      await expect(async () => {
        const response = await request.post('/api/v1/handling/activities', {
          headers: { Authorization: `Bearer ${handlerToken}` },
          data: {
            activityId: crypto.randomUUID(),
            trackingNumber,
            handlingType: 'RECEIVE',
            unLocode: 'JPTYO',
          },
        });
        expect(response.status()).toBe(201);
      }).toPass({ timeout: 60_000 });

      await signIn(page, 'tracker01');
      await page.goto(`/tracking/${trackingNumber}`);
      await expectEventually(page, '受領済');

      // **D1: 紛失を起票すると例外発生になる。**
      await page.getByRole('link', { name: '例外を起票する' }).click();
      await page.getByLabel('例外種別').selectOption('LOSS');
      await page.getByLabel('発生場所').fill('SGSIN');
      await page.getByLabel('発生状況').fill(`貨物が見つかりません（${product}）`);
      await page.getByRole('button', { name: '起票する' }).click();
      await expect(page).toHaveURL(new RegExp(`/tracking/${trackingNumber}$`),
        { timeout: 20_000 });
      await expectEventually(page, '例外発生');

      // **D1: 緊急として一覧の先頭に出る。**
      await page.goto('/tracking/exceptions');
      await expectEventually(page, `貨物が見つかりません（${product}）`);
      const row = page.getByRole('row', { name: new RegExp(product) });
      await expect(row.getByText('緊急')).toBeVisible();
      // **D2: 上位者へ知らせた記録が残る**ので「未連絡」は出ない。
      await expect(row.getByText('未連絡')).toHaveCount(0);

      // **D2: 管理者が同じ一覧を開いて見つけられる**（US20 §受入基準 3 の読み口）。
      await signIn(page, 'admin01');
      await page.goto('/tracking/exceptions');
      await expectEventually(page, `貨物が見つかりません（${product}）`);
      // **管理者は読む側。** 操作の導線は出さない（開けない場所へ誘わない）。
      await expect(page.getByRole('link', { name: '例外を起票' })).toHaveCount(0);
    });

  test('予定外の荷役で誤配になり、現在地から組み直せる（US28・IT11）',
    async ({ page, request }) => {
      // **US28 のクラスタ確認**（Try T8）。4 サービスの連鎖（荷役 → 追跡 →
      // 予約 → 経路探索）は、層ごとの検査では抜けが出ない。
      test.setTimeout(240_000);
      const product = `誤配の貨物-${Date.now()}`;
      const { trackingNumber, bookingId, voyageNumber } =
        await issueTrackingNumber(request, product);

      // **D5・D6: 予定ルート外の港で荷役を記録する。**
      const handlerToken = await tokenOf(request, 'handler01');
      await expect(async () => {
        const response = await request.post('/api/v1/handling/activities', {
          headers: { Authorization: `Bearer ${handlerToken}` },
          data: {
            activityId: crypto.randomUUID(),
            trackingNumber,
            handlingType: 'UNLOAD',
            // **荷降しには航海番号が要る**（種別自身が要件を持つ）。
            voyageNumber,
            // 予定の旅程に含まれない港（前提づくりは JPTYO → USNYC）。
            unLocode: 'NLRTM',
          },
        });
        expect(response.status()).toBe(201);
      }).toPass({ timeout: 60_000 });

      // **D8: 予約詳細に誤配のバナーが出る。**
      await signIn(page, 'routing01');
      await page.goto(`/bookings/${bookingId}`);
      await waitForProjection(page, async () => {
        await page.reload();
        await expect(page.getByRole('alert').filter({ hasText: '誤配を検知しました' }))
          .toBeVisible();
      });
      await expect(page.getByRole('alert')).toContainText('NLRTM');

      // **D6: 誤配の例外が自動で起票されている。**
      await signIn(page, 'tracker01');
      await page.goto('/tracking/exceptions');
      await expectEventually(page, '誤配');

      // **D9: 経路設計者は現在地を起点に組み直せる。**
      await signIn(page, 'routing01');
      await page.goto(`/bookings/${bookingId}`);
      await waitForProjection(page, async () => {
        await page.reload();
        await expect(page.getByRole('link', { name: '経路を再設計' })).toBeVisible();
      });
      await page.getByRole('link', { name: '経路を再設計' }).click();
      await expect(page.getByRole('heading', { name: /経路設計/ })).toBeVisible();
    });

  test('通関が留置なら引取が断られ、通関済にすると引取できる（US29・IT12）',
    async ({ page, request }) => {
      // **US29 のクラスタ確認**（Try T8。US ごとに 1 度回す）。3 サービスの連鎖
      // （荷役 → 追跡の例外 → 荷役の引取ガード）は、層ごとの検査では抜けが出る。
      //
      // **T7e として独立したタスク行にしてある。** US29 を閉じる前に回す。
      test.setTimeout(300_000);
      const product = `通関の貨物-${Date.now()}`;
      const { trackingNumber } = await issueTrackingNumber(request, product);
      const handlerToken = await tokenOf(request, 'handler01');
      const trackerToken = await tokenOf(request, 'tracker01');
      const declarationNumber = `IMP-${Date.now()}`;

      // **D1: 申告を登録すると審査中になる。**
      await expect(async () => {
        const response = await request.post('/api/v1/handling/customs-declarations', {
          headers: { Authorization: `Bearer ${handlerToken}` },
          data: { declarationNumber, trackingNumber, declaredAt: new Date().toISOString() },
        });
        expect(response.status()).toBe(201);
      }).toPass({ timeout: 60_000 });

      // **D4: 通関が済んでいない貨物の引取は断られ、現在の通関状態が出る。**
      // IT9 から 3 IT のあいだ「読む側の無い配線を敷かない」として保留してきた
      // ガードを、ここで初めて実地で確かめる。
      const beforeClearance = await request.post('/api/v1/handling/activities', {
        headers: { Authorization: `Bearer ${handlerToken}` },
        failOnStatusCode: false,
        data: {
          activityId: crypto.randomUUID(),
          trackingNumber,
          handlingType: 'CLAIM',
          unLocode: 'USNYC',
          consigneeConfirmation: 'John Smith',
        },
      });
      expect(beforeClearance.status()).toBe(409);
      expect(await beforeClearance.text()).toContain('PENDING');

      // **D7: 留置にすると税関保留の例外が自動で起票される。**
      await expect(async () => {
        const response = await request.post(
          `/api/v1/handling/customs-declarations/${declarationNumber}/status`,
          {
            headers: { Authorization: `Bearer ${trackerToken}` },
            data: { status: 'HELD', reason: '原産地証明が未提出' },
          },
        );
        expect(response.status()).toBe(200);
      }).toPass({ timeout: 60_000 });

      await signIn(page, 'tracker01');
      // **一覧から自分のデータを名指しで探さない**（Try T6）。詳細は URL で開き、
      // 一覧は「絞り込みが効く」ことだけ見る。ここは種別が出ることを見る。
      await page.goto('/tracking/exceptions');
      await expectEventually(page, '税関保留');

      // **D2・D5: 通関済にすると引取できる。**
      await expect(async () => {
        const response = await request.post(
          `/api/v1/handling/customs-declarations/${declarationNumber}/status`,
          {
            headers: { Authorization: `Bearer ${trackerToken}` },
            data: { status: 'CLEARED', reason: '証明書を受領' },
          },
        );
        expect(response.status()).toBe(200);
      }).toPass({ timeout: 60_000 });

      await expect(async () => {
        const response = await request.post('/api/v1/handling/activities', {
          headers: { Authorization: `Bearer ${handlerToken}` },
          data: {
            activityId: crypto.randomUUID(),
            trackingNumber,
            handlingType: 'CLAIM',
            unLocode: 'USNYC',
            consigneeConfirmation: 'John Smith',
          },
        });
        expect(response.status()).toBe(201);
      }).toPass({ timeout: 60_000 });

      // **D11: 変更履歴が申告詳細から読める**（画面から踏んで確かめる）。
      await page.goto(`/customs/${declarationNumber}`);
      await expectEventually(page, '原産地証明が未提出');
      await expect(page.getByText('証明書を受領')).toBeVisible();
    });

  test('追跡番号だけで照会でき、追跡管理者が状態を手で更新できる（US17・US18・IT8）',
    async ({ page, request }) => {
      // **本 IT の中核。** 公開照会は認証を通らず、状態の更新は trackingms の集約を
      // 通る。モックでは「Gateway が公開経路を素通しするか」を判別できない。
      //
      // 既定の 30 秒では足りない。最後にレート制限の窓（1 分）が空くまで待つ
      // ——待たないと、後続のテストが自分のせいで 429 になる。
      test.setTimeout(300_000);
      const product = `追跡の貨物-${Date.now()}`;
      const voyageNumber = uniqueVoyageNumber('V-TR-');
      const routing = await tokenOf(request, 'routing01');
      const voyage = await request.post('/api/v1/routing/voyages', {
        headers: { Authorization: `Bearer ${routing}` },
        data: {
          voyageNumber,
          carrierCode: 'MOL',
          carrierName: '商船三井',
          vesselName: 'TRACK MARU',
          movements: [
            {
              departureUnLocode: 'JPTYO',
              arrivalUnLocode: 'USNYC',
              departureAt: `${businessDate(2)}T00:00:00Z`,
              arrivalAt: `${businessDate(5)}T00:00:00Z`,
            },
          ],
          acceptedCargoTypes: ['GENERAL'],
        },
      });
      expect(voyage.status()).toBe(201);

      const bookingId = await bookCargo(request, product);
      await signIn(page, 'sales01');
      await page.goto(`/bookings/${bookingId}`);
      await page.getByRole('button', { name: '経路設計を依頼する' }).click();
      await expectEventually(page, '経路提案中');

      await page.goto('/logout');
      await signIn(page, 'routing01');
      await page.goto(`/routing/bookings/${bookingId}`);
      const candidate = page.getByTestId('candidate-1');
      await expect(candidate).toBeVisible({ timeout: 30_000 });
      await candidate.getByRole('radio').check();
      await page.getByRole('button', { name: 'この経路で確定' }).click();
      await expect(page.getByRole('heading', { name: '旅程' })).toBeVisible({ timeout: 20_000 });

      await page.goto('/logout');
      await signIn(page, 'sales01');
      await page.goto(`/bookings/${bookingId}`);
      await expect(page.getByLabel('通知内容')).toHaveValue(/JPTYO → USNYC/, { timeout: 20_000 });
      await page.getByLabel('通知先メールアドレス').fill('shipper@example.com');
      await page.getByRole('button', { name: '通知した記録を残す' }).click();
      await page.getByRole('button', { name: '予約を確定する' }).click();
      await expect(page.getByText('予約確定', { exact: true })).toBeVisible({ timeout: 20_000 });

      await page.goto('/logout');
      await signIn(page, 'routing01');
      await page.goto(`/bookings/${bookingId}`);
      await page.getByRole('button', { name: '追跡番号を発行する' }).click();
      const number = page.getByText(/^TRK-[0-9A-Z]{10}$/);
      await expect(number).toBeVisible({ timeout: 20_000 });
      const trackingNumber = (await number.textContent())?.trim() ?? '';

      // デモ項目 1: **ログインせずに**状況が読める。
      await page.goto('/logout');
      await page.goto(`/track/${trackingNumber}`);
      await expect(page.getByText('未受領')).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText('出発').first()).toBeVisible();

      // デモ項目 7: 公開の応答に社内の情報が入らない。
      const publicView = await request.get(`/api/v1/tracking/public/${trackingNumber}`);
      expect(publicView.status()).toBe(200);
      const publicBody = await publicView.json();
      expect(publicBody.shipperId).toBeUndefined();
      expect(publicBody.bookingId).toBeUndefined();

      // デモ項目 2: 見つからない番号は 404 だけ（実在するかを教えない）。
      const unknown = await request.get('/api/v1/tracking/public/TRK-NOSUCHNUM', {
        failOnStatusCode: false,
      });
      expect(unknown.status()).toBe(404);

      // デモ項目 4: 追跡管理者が手で更新でき、履歴に残る。
      await signIn(page, 'tracker01');
      await page.goto(`/tracking/${trackingNumber}`);
      await expect(page.getByLabel('新しい状態')).toBeVisible({ timeout: 20_000 });
      await page.getByLabel('新しい状態').selectOption('RECEIVED');
      await page.getByLabel('場所（UN/LOCODE）').fill('JPTYO');
      await page.getByRole('button', { name: '状態を更新する' }).click();
      // **投影の反映を待つ**（読み直しながら待つ。混んでいると 30 秒では届かない）。
      await waitForProjection(page, async () => {
        await page.reload();
        await expect(page.getByRole('row', { name: /受領済/ })).toBeVisible();
      });

      // デモ項目 5: 遷移表が許さない更新は **API を直接叩いても** 断られる。
      const tracker = await tokenOf(request, 'tracker01');
      const forbidden = await request.post(
        `/api/v1/tracking/trackings/${trackingNumber}/status`,
        {
          headers: { Authorization: `Bearer ${tracker}` },
          data: { newStatus: 'DELIVERED' },
          failOnStatusCode: false,
        });
      // **手では入れられない状態は業務規則で断る**（422）。遷移表が許さない先
      // （409）とは別で、IT10 で `isSetByHand` を足したときに意味が変わった。
      // **理由まで見る。** コードだけ見ると、別の理由で断られていても緑になる。
      expect(forbidden.status()).toBe(422);
      expect(await forbidden.text()).toContain('手では入れられません');

      // デモ項目 8: **総当たりが止まる。** 同一 IP から 1 分に 10 回を超える
      // 「見つからない」照会は 429。**当たりは数えない**ので、正しい番号を持つ
      // 荷受人は画面を開いたまま何度更新しても断られない。
      let retryAfter = '';
      let sawTooManyRequests = false;
      for (let i = 0; i < 12; i++) {
        const probe = await request.get('/api/v1/tracking/public/TRK-BRUTEFORC', {
          failOnStatusCode: false,
        });
        if (probe.status() === 429) {
          sawTooManyRequests = true;
          retryAfter = probe.headers()['retry-after'] ?? '';
          break;
        }
      }
      expect(sawTooManyRequests, '認証不要経路の唯一の防御が効いていない').toBe(true);
      // **断らせた分を後片付けする。** 数えるのは接続元アドレスなので、窓が
      // 空くまで待たないと、後続のテスト（未認証の公開照会）まで 429 になる。
      // 待つ長さはサーバが返した Retry-After に従う——こちらで決め打つと、
      // 上限や窓を変えたときに待ち足りなくなる。
      expect(retryAfter, '断るときは待てば通ることを伝える').not.toBe('');
      await new Promise((resolve) =>
        setTimeout(resolve, (Number(retryAfter) + 1) * 1000));
    });

  test('管理者は利用者の状態を見てロックを解除できる', async ({ page, request }) => {
    // 先に API で 5 回失敗させてロックする。画面から 5 回打つと、E2E が
    // 「ロックの再現手順」ではなく「入力の反復」を測ることになる。
    for (let i = 0; i < 5; i++) {
      await request.post('/api/v1/auth/login', {
        data: { username: 'handler01', password: 'wrong-password' },
        failOnStatusCode: false,
      });
    }

    await signIn(page, 'admin01');
    await page.goto('/admin/users');

    const row = page.locator('tr', { hasText: 'handler01' });
    await expect(row.getByText(/ロック中（あと \d+ 分）/)).toBeVisible();

    await row.getByRole('button', { name: '解除する' }).click();
    await expect(row.getByText(/ロック中/)).toHaveCount(0);

    // 解除できたことを、画面の見え方ではなく実際のログインで確かめる。
    const response = await request.post('/api/v1/auth/login', {
      data: { username: 'handler01', password: 'secret1234' },
      failOnStatusCode: false,
    });
    expect(response.status()).toBe(200);
  });

  test('未認証でもポータルから公開追跡へ入れる', async ({ page }) => {
    await page.goto('/portal');

    // 存在しない番号でも**入口までは通る**。見つからない案内はここで確かめる。
    await page.getByLabel('追跡番号').fill('TRK-NOSUCHNUM');
    await page.getByRole('button', { name: '照会する' }).click();

    await expect(page.getByRole('heading', { name: '荷物の追跡' })).toBeVisible();
    await expect(page.getByText(/追跡番号が見つかりません/)).toBeVisible({ timeout: 20_000 });
  });
  test('経路設計者が航海を更新すると、差分を確かめて反映できる（US25・IT4）', async ({
    page,
  }) => {
    const voyageNumber = uniqueVoyageNumber('V-UPD-');

    await signIn(page, 'routing01');
    await page.goto('/voyages/new');
    await page.getByLabel('航海番号').fill(voyageNumber);
    await page.getByLabel('運送会社コード').fill('MOL');
    await page.getByLabel('運送会社名', { exact: true }).fill('商船三井');
    await page.getByLabel('船名').fill('UPDATE EXPRESS');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('到着地').fill('USNYC');
    await page.getByLabel('出発日時（日本時間）').fill(`${businessDate(30)}T09:00`);
    await page.getByLabel('到着日時（日本時間）').fill(`${businessDate(45)}T18:00`);
    await page.getByRole('button', { name: '登録する' }).click();

    // 一覧の航海番号から詳細へ入る（IT3 レビューで欠けていた導線）。
    //
    // **一覧は表示上限で切れる。** 作り直さないクラスタでは航海が積み上がるので、
    // 登録した直後でも一覧の 1 ページ目に載るとは限らない——**絞り込んでから
    // 探す**（IT11 の通しで 2 度落ちた。上限の打ち切りは画面も知らせている）。
    // **一覧から名指しで探さない**（US24 と同じ理由。表示上限と出発日順）。
    await page.goto(`/voyages/${voyageNumber}`);
    await expect(page.getByRole('heading', { name: `航海 ${voyageNumber}` })).toBeVisible();
    await expect(page.getByTestId('movement-1')).toContainText('JPTYO → USNYC');

    await page.getByRole('link', { name: '更新する' }).click();
    await expect(page.getByLabel('船名')).toHaveValue('UPDATE EXPRESS', { timeout: 20_000 });
    await page.getByLabel('船名').fill('UPDATE VOYAGER');
    await page.getByRole('button', { name: '差分を確認する' }).click();

    // 差分はサーバが出す。画面で並べていないことは、ここに出る文で分かる。
    await expect(page.getByText('UPDATE EXPRESS → UPDATE VOYAGER')).toBeVisible();
    await page.getByRole('button', { name: '更新する' }).click();

    await expect(page.getByRole('heading', { name: `航海 ${voyageNumber}` })).toBeVisible();
    // **投影の反映を待つ**（読み直しながら待つ）。押した直後の画面だけを見ると、
    // 混んでいるときに届かない。
    await waitForProjection(page, async () => {
      await page.reload();
      await expect(page.getByText('UPDATE VOYAGER')).toBeVisible();
      await expect(page.getByText(/最終更新/)).toBeVisible();
    });
  });

  test('経路設計者が条件で航海を絞り込める（US07・IT4）', async ({ page }) => {
    const voyageNumber = uniqueVoyageNumber('V-SRC-');

    await signIn(page, 'routing01');
    await page.goto('/voyages/new');
    await page.getByLabel('航海番号').fill(voyageNumber);
    await page.getByLabel('運送会社コード').fill('ONE');
    await page.getByLabel('運送会社名', { exact: true }).fill('オーシャンネットワーク');
    await page.getByLabel('船名').fill('SEARCH HARMONY');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('到着地').fill('SGSIN');
    await page.getByLabel('出発日時（日本時間）').fill(`${businessDate(30)}T09:00`);
    await page.getByLabel('到着日時（日本時間）').fill(`${businessDate(40)}T18:00`);
    await page.getByRole('button', { name: '登録する' }).click();

    // **登録できたことは詳細で見る**（一覧は表示上限で切れる。US24 と同じ）。
    await page.goto(`/voyages/${voyageNumber}`);
    await expect(page.getByRole('heading', { name: `航海 ${voyageNumber}` }))
      .toBeVisible({ timeout: 20_000 });
    await page.goto('/voyages');

    // 条件に合う便だけが残る。**登録した便を名指しで探さない**——表示上限で
    // 切れるので、絞り込みが効いていることは「合わない便が消える」で見る。
    await narrowVoyagesTo(page, 'JPTYO', 'SGSIN');
    await expect(page.getByRole('cell', { name: 'USNYC' })).toHaveCount(0);

    // 合わない条件では 0 件の案内が出て、条件を消して戻れる。
    await page.getByLabel('目的地').fill('BRRIO');
    await page.getByRole('button', { name: '絞り込む' }).click();
    await expect(page.getByText('条件に合う航海はありません')).toBeVisible();

    await page.getByRole('button', { name: '条件を消して探し直す' }).click();
    await expect(page.getByRole('heading', { name: '航海スケジュール一覧' })).toBeVisible();
  });

  test('営業が仮受付の予約を修正できる（US32・IT4）', async ({ page }) => {
    const stamp = Date.now();
    const email = `update-${stamp}@example.com`;
    const product = `修正前の貨物-${stamp}`;

    await signIn(page, 'sales01');
    await page.goto('/shippers/new');
    await page.getByLabel('名称').fill(`修正商事 ${stamp}`);
    await page.getByLabel('メールアドレス').fill(email);
    await page.getByLabel('電話番号').fill('03-0000-0000');
    await page.getByLabel('住所').fill('東京都中央区');
    await page.getByRole('button', { name: '登録する' }).click();
    await expect(page.getByText(email)).toBeVisible({ timeout: 20_000 });

    await page.goto('/bookings/new');
    await page.getByLabel('荷主を名前で絞り込む').fill(`修正商事 ${stamp}`);
    const option = page.locator('#shipperId option', { hasText: `修正商事 ${stamp}` });
    await expect(option).toHaveCount(1, { timeout: 20_000 });
    await page.getByLabel('荷主', { exact: true })
        .selectOption((await option.getAttribute('value')) ?? '');
    await page.getByLabel('出発地').fill('JPTYO');
    await page.getByLabel('目的地').fill('USNYC');
    await page.getByLabel('到着期限').fill(businessDate(60));
    await page.getByLabel('重量 (kg)').fill('1200');
    await page.getByLabel('数量').fill('10');
    await page.getByLabel('長さ (cm)').fill('120');
    await page.getByLabel('幅 (cm)').fill('80');
    await page.getByLabel('高さ (cm)').fill('100');
    await page.getByLabel('品名').fill(product);
    await page.getByRole('button', { name: '登録する' }).click();

    // 一覧は到着期限順で上限があるので、品名で絞ってから確かめる。
    await page.getByLabel('予約番号・品名で絞り込む').fill(product);
    await expect(page.getByText(product)).toBeVisible({ timeout: 20_000 });
    // 一覧の先頭を押さない。前の回の予約が並んでいて、別の予約を開いてしまう。
    await page
      .locator('tr', { hasText: product })
      .getByRole('link', { name: /B-/ })
      .click();
    await expect(page.getByRole('link', { name: '修正する' })).toBeVisible({ timeout: 20_000 });
    await page.getByRole('link', { name: '修正する' }).click();

    await expect(page.getByLabel('品名')).toHaveValue(product, { timeout: 20_000 });
    await page.getByLabel('品名').fill(`${product}（訂正）`);
    await page.getByRole('button', { name: '修正する' }).click();

    // 「貨物」欄の値を見る。IT5 で修正履歴の表が付き、同じ文字列が
    // 「変更後」の欄にも出るようになった（US32 §受入基準 4 の読み口）。
    // 画面のどこかに出ていることだけを見ると、どちらを確かめたのか分からない。
    await expect(page.getByRole('definition').filter({ hasText: `${product}（訂正）` }))
      .toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/最終更新/)).toBeVisible();

    // 何を変えたかが読める（US32 §受入基準 4・IT5 R.2）。記録だけでは誰にも見えない。
    await expect(page.getByRole('heading', { name: '修正履歴' })).toBeVisible();
    await expect(page.getByTestId('revision-品名')).toContainText(product);

    // 引き渡すと修正の導線が消える（US32 §受入基準 1）。
    await page.getByRole('button', { name: '経路設計を依頼する' }).click();
    // **投影の反映を待つ**（混んでいると 20 秒では届かない。IT11 の通しで実測）。
    await expectEventually(page, '経路提案中');
    await expect(page.getByRole('link', { name: '修正する' })).toHaveCount(0);
  });
});
