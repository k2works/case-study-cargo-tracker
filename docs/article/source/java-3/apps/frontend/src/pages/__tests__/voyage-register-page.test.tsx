import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { VoyageRegisterPage } from '../voyage-register-page'

const LOCATIONS = [
  { unLocode: 'JPTYO', name: 'Tokyo' },
  { unLocode: 'CNSHA', name: 'Shanghai' },
  { unLocode: 'USLAX', name: 'Los Angeles' },
]

const VOYAGE = {
  voyageNumber: 'V0100',
  vesselName: 'さくら丸',
  carrierName: '日本郵船',
  supportedCargoTypes: ['GENERAL'],
  originUnLocode: 'JPTYO',
  originName: 'Tokyo',
  destinationUnLocode: 'USLAX',
  destinationName: 'Los Angeles',
  departureTime: '2026-10-01T00:00:00Z',
  arrivalTime: '2026-10-18T03:00:00Z',
  movements: [],
}

function renderPage(initialEntries = ['/routing/voyages/new']) {
  loginAs(['ROLE_ROUTING'])
  return renderWithProviders(<VoyageRegisterPage />, initialEntries)
}

async function selectPort(labelText: string, unLocode: string) {
  const select = await screen.findByLabelText(labelText)
  await within(select).findByRole('option', { name: /Tokyo/ })
  await userEvent.selectOptions(select, unLocode)
}

async function fillFirstLeg() {
  await userEvent.type(screen.getByLabelText('航海番号'), 'V0100')
  await userEvent.type(screen.getByLabelText('船名'), 'さくら丸')
  await userEvent.type(screen.getByLabelText('運送会社'), '日本郵船')
  await selectPort('1 区間目の出発地', 'JPTYO')
  await selectPort('1 区間目の到着地', 'USLAX')
  await userEvent.type(screen.getByLabelText('1 区間目の出発日時'), '2026-10-01T09:00')
  await userEvent.type(screen.getByLabelText('1 区間目の到着日時'), '2026-10-18T12:00')
}

describe('航海スケジュールの登録', () => {
  beforeEach(() => {
    server.use(http.get(API_PATHS.voyageLocations, () => HttpResponse.json(LOCATIONS)))
  })

  it('登録すると、登録できたことが分かる', async () => {
    server.use(http.post(API_PATHS.voyages, () => HttpResponse.json(VOYAGE, { status: 201 })))
    renderPage()

    await fillFirstLeg()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText(/航海 V0100 を登録しました/)).toBeInTheDocument()
  })

  /** 日時は業務の暦で送る。端末の設定（CI では UTC）で解釈すると、入力した時刻とずれる。 */
  it('入力した日時を業務タイムゾーンとして送る', async () => {
    let sent: { movements: { departureTime: string }[] } | null = null
    server.use(
      http.post(API_PATHS.voyages, async ({ request }) => {
        sent = (await request.json()) as typeof sent
        return HttpResponse.json(VOYAGE, { status: 201 })
      }),
    )
    renderPage()

    await fillFirstLeg()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    await screen.findByText(/登録しました/)
    expect(sent!.movements[0].departureTime).toBe('2026-10-01T00:00:00.000Z')
  })

  /**
   * 未入力を画面に残す。
   *
   * ブラウザ既定の検証は吹き出しで知らせるだけで、画面には何も残らない。
   * 「押しても何も起きない」と受け取られる。
   */
  it('足りない項目を画面のメッセージで示す', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('航海番号は必須です')
  })

  it('つながっていない区間の並びは送る前に断る', async () => {
    renderPage()

    await fillFirstLeg()
    await userEvent.click(screen.getByRole('button', { name: '寄港地を追加する' }))
    await selectPort('2 区間目の出発地', 'CNSHA')
    await selectPort('2 区間目の到着地', 'JPTYO')
    await userEvent.type(screen.getByLabelText('2 区間目の出発日時'), '2026-10-19T09:00')
    await userEvent.type(screen.getByLabelText('2 区間目の到着日時'), '2026-10-25T12:00')
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '2 区間目は、前の区間の到着地から出発するようにしてください',
    )
  })

  it('区間を追加すると、前の区間の到着地が出発地に入る', async () => {
    renderPage()

    await fillFirstLeg()
    await userEvent.click(screen.getByRole('button', { name: '寄港地を追加する' }))

    expect(await screen.findByLabelText('2 区間目の出発地')).toHaveValue('USLAX')
  })

  describe('同じ航海番号が既にあるとき', () => {
    const DIFFERENCE = {
      message: '同じ航海番号のスケジュールが既に登録されています',
      hasChanges: true,
      existing: VOYAGE,
      changes: [{ item: '船名', before: 'つばき丸', after: 'さくら丸' }],
    }

    /**
     * 何が変わるか分からないまま押させない。
     */
    it('更新前と更新後を並べて見せる', async () => {
      server.use(http.post(API_PATHS.voyages, () => HttpResponse.json(DIFFERENCE, { status: 409 })))
      renderPage()

      await fillFirstLeg()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))

      expect(await screen.findByText(/既に登録されています/)).toBeInTheDocument()
      expect(screen.getByText('つばき丸')).toBeInTheDocument()
      expect(screen.getByText('さくら丸')).toBeInTheDocument()
    })

    it('「やめる」を選べば何も変わらない', async () => {
      let updated = false
      server.use(
        http.post(API_PATHS.voyages, () => HttpResponse.json(DIFFERENCE, { status: 409 })),
        http.put(`${API_PATHS.voyages}/:voyageNumber`, () => {
          updated = true
          return HttpResponse.json(VOYAGE)
        }),
      )
      renderPage()

      await fillFirstLeg()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))
      await userEvent.click(await screen.findByRole('button', { name: 'やめる' }))

      expect(updated).toBe(false)
      expect(screen.queryByText(/既に登録されています/)).not.toBeInTheDocument()
    })

    it('上書きを選ぶと更新できる', async () => {
      server.use(
        http.post(API_PATHS.voyages, () => HttpResponse.json(DIFFERENCE, { status: 409 })),
        http.put(`${API_PATHS.voyages}/:voyageNumber`, () => HttpResponse.json(VOYAGE)),
      )
      renderPage()

      await fillFirstLeg()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))
      await userEvent.click(await screen.findByRole('button', { name: 'この内容で上書きする' }))

      expect(await screen.findByText(/航海 V0100 を登録しました/)).toBeInTheDocument()
    })

    /** 差分の無い上書きに「本当に上書きしますか」と聞くのは、判断できない問いである。 */
    it('内容が同じなら「変更はありません」と伝えて上書きを促さない', async () => {
      server.use(
        http.post(API_PATHS.voyages, () =>
          HttpResponse.json(
            {
              message: '同じ航海番号のスケジュールが既に登録されています。変更はありません',
              hasChanges: false,
              existing: VOYAGE,
              changes: [],
            },
            { status: 409 },
          ),
        ),
      )
      renderPage()

      await fillFirstLeg()
      await userEvent.click(screen.getByRole('button', { name: '登録する' }))

      expect(await screen.findByText(/変更はありません/)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'この内容で上書きする' })).not.toBeInTheDocument()
    })
  })

  /** 番号を打ち直させると、打ち間違いで別の航海ができる。 */
  describe('一覧から更新に来たとき', () => {
    const EXISTING = {
      ...VOYAGE,
      voyageNumber: 'V0200',
      vesselName: 'つばき丸',
      carrierName: '商船三井',
      supportedCargoTypes: ['GENERAL', 'HAZARDOUS'],
      destinationUnLocode: 'CNSHA',
      destinationName: 'Shanghai',
      movements: [
        {
          departureUnLocode: 'JPTYO',
          departureName: 'Tokyo',
          arrivalUnLocode: 'CNSHA',
          arrivalName: 'Shanghai',
          departureTime: '2026-10-01T00:00:00Z',
          arrivalTime: '2026-10-03T09:00:00Z',
        },
      ],
    }

    beforeEach(() => {
      server.use(
        // :voyageNumber は /locations にも一致する。先に評価される地点を前に置く
        http.get(API_PATHS.voyageLocations, () => HttpResponse.json(LOCATIONS)),
        http.get(`${API_PATHS.voyages}/:voyageNumber`, () => HttpResponse.json(EXISTING)),
      )
    })

    it('航海番号が入っている', async () => {
      renderPage(['/routing/voyages/new?voyageNumber=V0200'])

      expect(await screen.findByLabelText('航海番号')).toHaveValue('V0200')
    })

    /**
     * 10 区間ある航海の到着を 1 日ずらすために全部打ち直させると、
     * その過程で別の項目が変わる。既存の内容を読み込んで初期値にする。
     */
    it('既存の内容が初期値として入っていて、時刻だけ直せる', async () => {
      renderPage(['/routing/voyages/new?voyageNumber=V0200'])

      // 読み込みが終わるまで待つ（入力欄自体は最初から空で存在する）
      await screen.findByDisplayValue('つばき丸')
      expect(screen.getByLabelText('船名')).toHaveValue('つばき丸')
      expect(screen.getByLabelText('運送会社')).toHaveValue('商船三井')
      expect(screen.getByLabelText('1 区間目の出発地')).toHaveValue('JPTYO')
      expect(screen.getByLabelText('1 区間目の到着地')).toHaveValue('CNSHA')
      // 業務タイムゾーンで表示する（09:00Z ではなく 09:00 と入力した値）
      expect(screen.getByLabelText('1 区間目の出発日時')).toHaveValue('2026-10-01T09:00')
      expect(screen.getByLabelText('1 区間目の到着日時')).toHaveValue('2026-10-03T18:00')
      expect(screen.getByLabelText('危険物')).toBeChecked()
    })

    /** 「登録」と出ていると、更新のつもりの人が別の航海を作ったかと不安になる。 */
    it('見出しが更新であることを示す', async () => {
      renderPage(['/routing/voyages/new?voyageNumber=V0200'])

      expect(
        await screen.findByRole('heading', { name: '航海スケジュールの更新' }),
      ).toBeInTheDocument()
    })
  })
})
