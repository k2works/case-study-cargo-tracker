import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { BookingRegisterPage } from '../booking-register-page'

/**
 * 貨物予約の登録画面（US04・US05）。
 *
 * 種別による項目の出し分けと、種別を戻したときに前の入力が残らないことを確かめる。
 * 画面に出ていない値が黙って送られると、サーバが拒んだ理由が利用者に結びつかない。
 */

const SHIPPERS = [
  {
    id: 1,
    shipperCode: 'SHP-000001',
    type: 'CORPORATE',
    name: '丸紅商事株式会社',
    email: 'corp@example.com',
    address: '東京都',
    phone: null,
    contractNumber: 'CN-2026-0001',
    discountRatePercent: 10,
  },
]

const LOCATIONS = [
  { unLocode: 'JPTYO', name: 'Tokyo' },
  { unLocode: 'USLAX', name: 'Los Angeles' },
]

function renderPage() {
  loginAs(['ROLE_SALES'])
  return renderWithProviders(<BookingRegisterPage />)
}

async function fillRequired() {
  // 選択肢の読み込みを待つ。待たずに選ぶと「その値は無い」で落ち、
  // 画面の不具合と取り違える
  await screen.findByRole('option', { name: /丸紅商事株式会社/ })
  await userEvent.selectOptions(screen.getByLabelText('荷主'), '1')
  await userEvent.type(screen.getByLabelText('重量（kg）'), '1000')
  await userEvent.selectOptions(await screen.findByLabelText('出発地'), 'JPTYO')
  await userEvent.selectOptions(screen.getByLabelText('目的地'), 'USLAX')
  await userEvent.type(screen.getByLabelText('到着期限'), '2027-09-20')
}

describe('貨物予約の登録', () => {
  beforeEach(() => {
    server.use(
      http.get(API_PATHS.shippers, () => HttpResponse.json(SHIPPERS)),
      http.get(API_PATHS.bookingLocations, () => HttpResponse.json(LOCATIONS)),
    )
  })

  it('荷主と地点は一覧から選ぶ（コードの直接入力はさせない）', async () => {
    renderPage()

    // 打ち間違いが「登録の失敗」としてしか返らない形にしない
    expect(await screen.findByRole('option', { name: /丸紅商事株式会社/ })).toBeInTheDocument()
    // 出発地と目的地の両方に同じ地点が並ぶため、件数で確かめる
    await waitFor(() =>
      expect(screen.getAllByRole('option', { name: /Tokyo（JPTYO）/ })).toHaveLength(2),
    )
  })

  it('一般貨物のあいだは危険物・温度の項目を出さない', async () => {
    renderPage()

    expect(screen.queryByLabelText('UN 番号')).toBeNull()
    expect(screen.queryByLabelText('保管温度の下限（℃）')).toBeNull()
  })

  it('危険物を選ぶと申告欄が現れる', async () => {
    renderPage()

    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS')

    expect(screen.getByLabelText('危険物クラス')).toBeInTheDocument()
    expect(screen.getByLabelText('UN 番号')).toBeInTheDocument()
    expect(screen.getByLabelText('正式品名')).toBeInTheDocument()
  })

  it('冷凍・冷蔵を選ぶと温度条件が現れる', async () => {
    renderPage()

    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'REFRIGERATED')

    expect(screen.getByLabelText('保管温度の下限（℃）')).toBeInTheDocument()
    expect(screen.getByLabelText('保管温度の上限（℃）')).toBeInTheDocument()
  })

  it('種別を戻すと、前の種別で入れた追加情報は残らない', async () => {
    renderPage()

    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS')
    await userEvent.type(screen.getByLabelText('UN 番号'), 'UN1263')
    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'GENERAL')
    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS')

    // 画面に出ていない値が黙って復活すると、別の内容で登録されたことに気づけない
    expect(screen.getByLabelText('UN 番号')).toHaveValue('')
  })

  it('荷主を選ばずに登録しようとすると、直すべき箇所を示す', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('荷主を選んでください')
  })

  it('到着期限を入れずに登録しようとすると、直すべき箇所を示す', async () => {
    renderPage()

    await screen.findByRole('option', { name: /丸紅商事株式会社/ })
    await userEvent.selectOptions(screen.getByLabelText('荷主'), '1')
    await userEvent.selectOptions(screen.getByLabelText('出発地'), 'JPTYO')
    await userEvent.selectOptions(screen.getByLabelText('目的地'), 'USLAX')
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('到着期限を入力してください')
  })

  it('種別で不要な追加情報は送らない', async () => {
    let sent: Record<string, unknown> = {}
    server.use(
      http.post(API_PATHS.bookings, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ bookingId: 'BKG-2026000001' }, { status: 201 })
      }),
    )

    renderPage()
    await fillRequired()
    // 危険物の欄に入れてから一般貨物へ戻す
    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'HAZARDOUS')
    await userEvent.type(screen.getByLabelText('UN 番号'), 'UN1263')
    await userEvent.selectOptions(screen.getByLabelText('貨物種別'), 'GENERAL')
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    // 一般貨物に危険物申告が付くと、サーバが拒む（付けすぎも誤り）
    await waitFor(() => expect(sent.unNumber).toBeNull())
    expect(sent.hazardousClass).toBeNull()
    expect(sent.minCelsius).toBeNull()
  })

  it('サーバが理由を添えて拒んだら、その理由を見せる', async () => {
    server.use(
      http.post(API_PATHS.bookings, () =>
        HttpResponse.json(
          { message: '到着期限に過去の日付は指定できません: 2020-01-01' },
          { status: 400 },
        ),
      ),
    )

    renderPage()
    await fillRequired()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    // 「時間をおいて再度」では、何を直せばよいか分からない
    expect(await screen.findByRole('alert')).toHaveTextContent('到着期限に過去の日付')
  })

  it('理由の分からない失敗は、やり直しを促す', async () => {
    server.use(http.post(API_PATHS.bookings, () => HttpResponse.error()))

    renderPage()
    await fillRequired()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('時間をおいて')
  })
})
