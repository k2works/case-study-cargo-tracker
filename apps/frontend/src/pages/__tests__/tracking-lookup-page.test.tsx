import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { handlingActivities, handlingHandlers } from '../../mocks/handlers/handling'
import {
  forceLookupThrottle,
  resetLookupThrottle,
  raiseExceptionForTest,
  resetTrackings,
  trackingHandlers,
  trackings,
} from '../../mocks/handlers/tracking'
import { server } from '../../test/msw/server'
import { renderWithProviders } from '../../test/render'
import { TrackingLookupPage } from '../tracking-lookup-page'

/**
 * 公開の追跡照会（US18）。**認証不要**。
 *
 * このシステムで唯一ログインを要さない業務画面である。何を出すかだけでなく、
 * **何を出さないか**（[ADR-024] 決定 5）も検査する。
 */
describe('追跡情報の照会（US18）', () => {
  beforeEach(() => {
    handlingActivities.length = 0
    resetTrackings()
    // 上限の窓はテストをまたいで残る。開け直さないと、あとのテストが 429 を踏む
    resetLookupThrottle()
    // ブラウザ用モックと同じハンドラを使う。テスト用に別のものを組み立てると、
    // 本物との読み比べ（IT5 Try 4）の対象が 1 つ増える
    server.use(...handlingHandlers, ...trackingHandlers)
  })

  function renderPage(trackingNumber: string) {
    return renderWithProviders(<TrackingLookupPage />, [`/tracking/${trackingNumber}`], undefined, {
      path: '/tracking/:trackingNumber',
    })
  }

  /** US18-1・US18-2。 */
  it('追跡番号で、状態・現在地・到着予定日を照会できる', async () => {
    renderPage('TRK-20260823-0001')

    expect(await screen.findByText('受領待ち')).toBeInTheDocument()
    expect(screen.getByText('Tokyo')).toBeInTheDocument()
    expect(screen.getByText('2027-09-15')).toBeInTheDocument()
  })

  /**
   * <strong>緊急が荷主に届く</strong>（[ADR-025] 決定 2・[IT8 Try 4]）。
   *
   * <p>この 1 本が、<strong>集約 → 応答 → モック → 画面</strong>のどこで潰しても赤になる。
   * IT8 は緊急を「決めた」だけで、公開応答まで届くことを一度も確かめていなかった。
   *
   * <p><strong>急かす言葉は使わない。</strong>「至急のご連絡が必要です」は、何をすれば
   * よいか伝えずに緊急だけを渡す。IT9 で営業に例外の導線が入り（返済枠 0.9）、
   * <strong>案内した先に人がいる状態</strong>になって初めて案内は案内になる。
   *
   * <p><strong>種別は書かない</strong>（[ADR-024] 決定 3）。上の欄で隠したものを、
   * ここで出さない。
   */
  it('紛失のときだけ、次の行動を案内する', async () => {
    trackings[0].status = 'EXCEPTION'
    trackings[0].statusBefore = 'ONBOARD_CARRIER'
    // **緊急かどうかを検査が決めない。**種別から導く（定義を変えたら赤になる）
    raiseExceptionForTest(
      'TRK-20260823-0001',
      'LOST',
      '所在が確認できません',
      '2027-09-05T00:00:00.000Z',
    )
    renderPage('TRK-20260823-0001')

    expect(
      await screen.findByText(/ご依頼元へのご連絡をおすすめします/),
    ).toBeInTheDocument()
    // **急かす言葉は使わない**
    expect(screen.queryByText(/至急/)).not.toBeInTheDocument()
    // **種別は書かない**（[ADR-024] 決定 3）
    expect(screen.queryByText(/紛失/)).not.toBeInTheDocument()
  })

  /** 遅延・破損では案内を出さない。**緊急は紛失だけである**。 */
  it('遅延では、連絡をすすめる案内を出さない', async () => {
    trackings[0].status = 'EXCEPTION'
    trackings[0].statusBefore = 'ONBOARD_CARRIER'
    raiseExceptionForTest(
      'TRK-20260823-0001',
      'DELAY',
      '台風により遅延',
      '2027-09-05T00:00:00.000Z',
    )
    renderPage('TRK-20260823-0001')

    expect(await screen.findByText(/お荷物に問題が起きています/)).toBeInTheDocument()
    expect(
      screen.queryByText(/ご依頼元へのご連絡をおすすめします/),
    ).not.toBeInTheDocument()
  })

  /**
   * 上限に当たったときに何が見えるか（[ADR-024] 決定 6）。
   *
   * **本物にあってモックに無い応答は、画面が一度も通らない経路になる。** 429 を
   * 「ただいま照会できません」と出すと、荷主は障害だと受け取って何度も押し、
   * 状況を悪くする。何が起きたのかを伝える。
   */
  it('照会が多すぎるときは、待てばよいと分かる文言を出す', async () => {
    forceLookupThrottle()
    renderPage('TRK-20260823-0001')

    expect(await screen.findByText(/照会が多すぎます/)).toBeInTheDocument()
  })

  /**
   * US18-4。**行き止まりにしない**——同じ画面で打ち直せる。
   *
   * 文言は**サーバが返したものをそのまま出す**。画面が自分の文を持つと、
   * サーバ・モック・画面で 3 つの写しができ、案内を足してもどれか 1 つが
   * 古いまま残る（IT9 返済枠 0.3 で実際にそうなっていた）。
   *
   * 番号の形まで確かめるのは、**層のどこを潰しても赤になる 1 本**にするため。
   * サーバの文言・モックの文言・画面の表示のどれを削っても、これが落ちる。
   */
  it('存在しない追跡番号は、サーバが返した案内をそのまま出す', async () => {
    renderPage('TRK-20260823-9999')

    expect(await screen.findByText(/追跡番号が見つかりません/)).toBeInTheDocument()
    expect(screen.getByText(/TRK- で始まります/)).toBeInTheDocument()
    expect(screen.getByText(/予約番号 BKG- では引けません/)).toBeInTheDocument()
    expect(screen.getByLabelText('追跡番号')).toBeEnabled()
  })

  /**
   * US18-2。**分からなければ「未定」**。
   *
   * 0 や今日で埋めると、荷主は「今日着く」と読む。
   */
  it('経路が決まっていなければ、到着予定日は「未定」と出る', async () => {
    // 経路が決まっていない貨物を作る。モックの状態を直接動かす
    trackings[0].estimatedArrival = null

    renderPage('TRK-20260823-0001')

    expect(await screen.findByText('未定')).toBeInTheDocument()
  })

  /**
   * US18-3。**荷役の記録と手動更新が 1 本に並ぶ。**
   *
   * 別々に出すと、荷主は貨物に何が起きたかを 2 つの表から組み立てることになる。
   */
  it('荷役の記録が、経過に時系列で並ぶ', async () => {
    handlingActivities.push({
      id: 1,
      bookingId: 'BKG-2026000004',
      type: 'RECEIVE',
      locationUnLocode: 'JPTYO',
      locationName: 'Tokyo',
      completionTime: '2027-09-02T00:00:00Z',
      operatorName: 'handler01',
      voyageNumber: null,
      consigneeConfirmation: null,
      offRoute: false,
    })

    renderPage('TRK-20260823-0001')

    const history = await screen.findByRole('table')
    expect(within(history).getByText('受領済み')).toBeInTheDocument()
  })

  /**
   * **返さないものを出さない**（[ADR-024] 決定 5）。
   *
   * 認証が無い以上、追跡番号を手に入れた誰もが見る。荷役の作業者名や予定外だった
   * 事実は、荷主に伝えるものではなく社内の手がかりである。
   */
  it('予約番号・作業者・航海番号は表示しない', async () => {
    handlingActivities.push({
      id: 1,
      bookingId: 'BKG-2026000004',
      type: 'LOAD',
      locationUnLocode: 'JPTYO',
      locationName: 'Tokyo',
      completionTime: '2027-09-02T00:00:00Z',
      operatorName: 'handler01',
      voyageNumber: 'V-SEED-3',
      consigneeConfirmation: null,
      offRoute: true,
    })

    renderPage('TRK-20260823-0001')
    await screen.findByRole('table')

    const body = document.body.textContent ?? ''
    expect(body, '予約番号が荷主に見えている').not.toContain('BKG-')
    expect(body, '作業者が荷主に見えている').not.toContain('handler01')
    expect(body, '航海番号が荷主に見えている').not.toContain('V-SEED-3')
    expect(body, '予定外だった事実が荷主に見えている').not.toContain('予定外')
  })

  /**
   * US17-4・US19-3・US20-4。**代替であることを画面に書く**（[ADR-024] 決定 9）。
   *
   * 書かないと、荷主は「メールが来ないのは不具合」と受け取る。
   */
  it('お知らせは画面に出し、メールを送っていないことを書く', async () => {
    renderPage('TRK-20260823-0001')

    expect(await screen.findByRole('heading', { name: 'お知らせ' })).toBeInTheDocument()
    expect(screen.getByText(/メールは送っていません/)).toBeInTheDocument()
  })

  /**
   * [ADR-024] 決定 9。**お知らせの中身が画面に出る**。
   *
   * 文言の説明だけを見ると、一覧の描画を丸ごと消しても緑になる。
   */
  it('お知らせの中身が、画面に並ぶ', async () => {
    trackings[0].notices.push({
      noticedAt: '2027-09-02T00:00:00Z',
      message: 'お荷物の状況が「受領済み」になりました。',
    })

    renderPage('TRK-20260823-0001')

    expect(await screen.findByText(/お荷物の状況が「受領済み」になりました。/))
      .toBeInTheDocument()
  })

  /**
   * **荷主に UTC の生の日時を出さない**（[ADR-010]）。
   *
   * `2027-09-02T00:00:00.000Z` と並ぶと、荷主は「深夜 0 時に受領した」と読む。
   * 入力側は業務の暦で解釈しているのに、出力側だけ揃っていない形になる。
   */
  it('経過の日時は、業務の暦で読める形で出る', async () => {
    handlingActivities.push({
      id: 1,
      bookingId: 'BKG-2026000004',
      type: 'RECEIVE',
      locationUnLocode: 'JPTYO',
      locationName: 'Tokyo',
      completionTime: '2027-09-02T00:00:00Z',
      operatorName: 'handler01',
      voyageNumber: null,
      consigneeConfirmation: null,
      offRoute: false,
    })

    renderPage('TRK-20260823-0001')
    const history = await screen.findByRole('table')

    // 業務の暦（Asia/Tokyo）では 9 時
    expect(within(history).getByText('2027-09-02 09:00')).toBeInTheDocument()
    // UTC の生の日時が漏れていない
    expect(document.body.textContent ?? '').not.toContain('T00:00:00')
  })

  /** 番号を打ち直すと、その番号の照会に移る。 */
  it('別の追跡番号を入れて照会し直せる', async () => {
    const user = userEvent.setup()
    renderPage('TRK-20260823-9999')
    await screen.findByText(/追跡番号が見つかりません/)

    await user.clear(screen.getByLabelText('追跡番号'))
    await user.type(screen.getByLabelText('追跡番号'), 'TRK-20260823-0001')
    await user.click(screen.getByRole('button', { name: '追跡する' }))

    expect(await screen.findByText('受領待ち')).toBeInTheDocument()
  })
})
