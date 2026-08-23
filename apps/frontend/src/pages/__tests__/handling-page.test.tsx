import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { handlingActivities, handlingHandlers } from '../../mocks/handlers/handling'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { HandlingPage } from '../handling-page'

/**
 * 荷役作業の記録（US15・US16）。
 *
 * 荷役作業員は追跡番号を起点に作業する。予約番号は知らない。
 */
/** 種別の表示名はサーバが持つ。ここは「選択肢が届いたか」を待つためだけに使う。 */
const TYPE_LABELS: Record<string, string> = {
  RECEIVE: '受領',
  LOAD: '積込',
  UNLOAD: '荷降し',
  CLAIM: '引取',
}

describe('荷役作業の記録（US15）', () => {
  beforeEach(() => {
    handlingActivities.length = 0
    loginAs(['ROLE_HANDLER'])
    // ブラウザ用モックと同じハンドラを使う。テスト用に別のものを組み立てると、
    // 本物との読み比べ（IT5 Try 4）の対象が 1 つ増える
    server.use(...handlingHandlers)
  })

  function renderPage() {
    return renderWithProviders(<HandlingPage />, ['/handling'], undefined, { path: '/handling' })
  }

  /** 種別の選択肢はサーバが返す。届く前に選ぼうとしても、まだ選択肢が無い。 */
  async function selectType(user: ReturnType<typeof userEvent.setup>, type: string) {
    await screen.findByRole('option', { name: TYPE_LABELS[type] })
    await user.selectOptions(screen.getByLabelText(/作業の種別/), type)
  }

  async function fillAndSubmit(overrides: Record<string, string> = {}) {
    const user = userEvent.setup()
    await user.type(
      await screen.findByLabelText(/追跡番号/),
      overrides.trackingNumber ?? 'TRK-20260823-0001',
    )
    await selectType(user, overrides.type ?? 'RECEIVE')
    await user.selectOptions(screen.getByLabelText(/作業場所/), overrides.location ?? 'JPTYO')
    await user.clear(screen.getByLabelText(/作業日時/))
    await user.type(
      screen.getByLabelText(/作業日時/),
      overrides.completionTime ?? '2027-09-02T09:00',
    )
    if (overrides.voyageNumber !== undefined) {
      await user.type(screen.getByLabelText(/航海番号/), overrides.voyageNumber)
    }
    if (overrides.consigneeConfirmation !== undefined) {
      await user.type(
        screen.getByLabelText(/荷受人の確認/),
        overrides.consigneeConfirmation,
      )
    }
    await user.click(screen.getByRole('button', { name: '記録する' }))
    return user
  }

  /** US15-1〜US15-4。 */
  it('追跡番号で貨物を特定して受領を記録できる', async () => {
    renderPage()

    await fillAndSubmit()

    expect(await screen.findByText(/記録しました/)).toBeInTheDocument()
    // 履歴に出ることで、作業員は登録できたかが分かる
    const history = await screen.findByRole('table')
    expect(within(history).getByText('受領')).toBeInTheDocument()
    expect(within(history).getByText(/Tokyo/)).toBeInTheDocument()
  })

  /**
   * **登録後も追跡番号を残す。**
   *
   * 同じ貨物に続けて記録するのが荷役の実際の使い方である。全部空にすると、
   * 作業員は追跡番号を毎回打ち直すことになる。
   */
  it('記録したあとも追跡番号は残る', async () => {
    renderPage()

    await fillAndSubmit()
    await screen.findByText(/記録しました/)

    expect(await screen.findByLabelText(/追跡番号/)).toHaveValue('TRK-20260823-0001')
  })

  /** US15-6。番号を読み違えるのが最も多い。何を直せばよいかを伝える。 */
  it('存在しない追跡番号は理由を出す', async () => {
    renderPage()

    await fillAndSubmit({ trackingNumber: 'TRK-99999999-9999' })

    expect(await screen.findByText(/番号を確かめてください/)).toBeInTheDocument()
  })

  /**
   * US15-7・[ADR-023] 決定 3。
   *
   * **警告は出すが記録は拒まない。** 現場ではすでに作業が終わっており、拒むと実際に
   * 起きたことがどこにも残らない。
   */
  it('予定ルート外の作業は、警告を出したうえで記録に残る', async () => {
    renderPage()

    await fillAndSubmit({ type: 'UNLOAD', location: 'SGSIN', voyageNumber: 'V-SEED-3' })

    expect(await screen.findByText(/予定と違う場所/)).toBeInTheDocument()
    const history = await screen.findByRole('table')
    expect(within(history).getByText('荷降し')).toBeInTheDocument()
    expect(within(history).getByText(/予定外/)).toBeInTheDocument()
  })

  /** US15-2。要件はサーバが答える。画面は結果を出すだけ。 */
  it('積込を選ぶと航海番号の入力欄が出る', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(screen.queryByLabelText(/航海番号/)).not.toBeInTheDocument()
    await selectType(user, 'LOAD')

    expect(await screen.findByLabelText(/航海番号/)).toBeInTheDocument()
  })

  describe('引取（US16）', () => {
    /** US16-1。 */
    it('引取を選ぶと荷受人確認の欄が出る', async () => {
      const user = userEvent.setup()
      renderPage()

      expect(screen.queryByLabelText(/荷受人の確認/)).not.toBeInTheDocument()
      await selectType(user, 'CLAIM')

      expect(await screen.findByLabelText(/荷受人の確認/)).toBeInTheDocument()
    })

    /**
     * US16-2・成功基準 3（画面層）。
     *
     * 通関ガード（US29・IT9）が無い IT7 では、これが唯一の歯止めである。
     */
    it('荷受人の確認がないと引取は記録できない', async () => {
      renderPage()

      await fillAndSubmit({ type: 'CLAIM', location: 'USLAX' })

      expect(await screen.findByText(/荷受人の確認は必須です/)).toBeInTheDocument()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
    })

    /** US16-3。 */
    it('荷受人の確認を入れると引取が記録される', async () => {
      renderPage()

      await fillAndSubmit({
        type: 'CLAIM',
        location: 'USLAX',
        consigneeConfirmation: '山田太郎（受取担当）',
      })

      const history = await screen.findByRole('table')
      expect(within(history).getByText('引取')).toBeInTheDocument()
    })

    /**
     * **代替であることを画面に書く**（[ADR-023] 決定 4）。
     *
     * 通関の確認が仕組みとして働いていないことを伝えないと、作業員は
     * 「システムが通関を見ている」と受け取る。
     */
    it('通関の確認が仕組みでは行われないことを、引取の操作のそばに書く', async () => {
      const user = userEvent.setup()
      renderPage()

      await selectType(user, 'CLAIM')

      expect(await screen.findByText(/通関の確認は、まだ仕組みでは行われません/))
        .toBeInTheDocument()
    })
  })

  /**
   * **荷主への通知はまだ行われない**（US15-5 は代替・IT8 の US19）。
   *
   * 書かないと、作業員は「記録すれば荷主に伝わる」と受け取る。
   */
  it('荷主へ自動で通知されないことを画面に書く', async () => {
    renderPage()

    expect(await screen.findByText(/荷主へは自動で通知されません/)).toBeInTheDocument()
  })
})
