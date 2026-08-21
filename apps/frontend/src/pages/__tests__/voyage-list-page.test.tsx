import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { businessToday } from '../../lib/business-time'
import { VoyageListPage } from '../voyage-list-page'

const LOCATIONS = [
  { unLocode: 'JPTYO', name: 'Tokyo' },
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

function renderPage() {
  loginAs(['ROLE_ROUTING'])
  return renderWithProviders(<VoyageListPage />)
}

describe('航海スケジュールの一覧', () => {
  beforeEach(() => {
    server.use(
      http.get(API_PATHS.voyageLocations, () => HttpResponse.json(LOCATIONS)),
      http.get(API_PATHS.voyages, () =>
        HttpResponse.json({
          voyages: [VOYAGE],
          totalCount: 1,
          limit: 50,
          truncated: false,
        }),
      ),
    )
  })

  it('航海と船名・運送会社・運べる貨物を並べる', async () => {
    renderPage()

    expect(await screen.findByText('V0100')).toBeInTheDocument()
    // どの船かが分からないと、荷役と問い合わせで貨物を追えない
    expect(screen.getByText('さくら丸')).toBeInTheDocument()
    expect(screen.getByText('日本郵船')).toBeInTheDocument()
    // 「積みたい貨物」の選択肢にも同じ言葉が出るため、行の中で確かめる
    const row = screen.getByText('V0100').closest('tr')
    expect(within(row!).getByText('一般貨物')).toBeInTheDocument()
  })

  /** 日時は業務の暦で見せる。UTC のまま出すと、出港の日が 1 日ずれて見える。 */
  it('日時は業務タイムゾーンで表示する', async () => {
    renderPage()

    expect(await screen.findByText('2026-10-01 09:00')).toBeInTheDocument()
  })

  it('出発地・目的地は一覧から選ぶ（UN/LOCODE を直接入力させない）', async () => {
    renderPage()

    const origin = await screen.findByLabelText('出発地')
    expect(origin.tagName).toBe('SELECT')
    // 選択肢の読み込みを待つ。待たずに選ぶと「その値は無い」で落ち、画面の不具合と取り違える
    await within(origin).findByRole('option', { name: /Tokyo/ })
    await userEvent.selectOptions(origin, 'JPTYO')
    expect(origin).toHaveValue('JPTYO')
  })

  /**
   * 0 件で終わらせない。
   *
   * 条件のどれが効きすぎたのか分からないまま「該当なし」だけ残ると、
   * 経路設計者は毎回条件を 1 つずつ消して試すことになる。
   */
  it('条件に合う航海が無いとき、条件を緩めて探し直せる', async () => {
    server.use(
      http.get(API_PATHS.voyages, () =>
        HttpResponse.json({
          voyages: [],
          totalCount: 0,
          limit: 50,
          truncated: false,
        }),
      ),
    )
    renderPage()

    const origin = await screen.findByLabelText('出発地')
    await within(origin).findByRole('option', { name: /Tokyo/ })
    await userEvent.selectOptions(origin, 'JPTYO')
    await userEvent.click(screen.getByRole('button', { name: '検索する' }))

    expect(await screen.findByText(/条件に合う航海はありませんでした/)).toBeInTheDocument()
    const retry = screen.getByRole('button', {
      name: '条件をすべて外して探し直す',
    })

    server.use(
      http.get(API_PATHS.voyages, () =>
        HttpResponse.json({
          voyages: [VOYAGE],
          totalCount: 1,
          limit: 50,
          truncated: false,
        }),
      ),
    )
    await userEvent.click(retry)

    expect(await screen.findByText('V0100')).toBeInTheDocument()
  })

  /** 黙って切ると「条件に合う航海はこれで全部だ」と読まれる。 */
  it('上限で切ったことを画面が伝える', async () => {
    server.use(
      http.get(API_PATHS.voyages, () =>
        HttpResponse.json({
          voyages: [VOYAGE],
          totalCount: 120,
          limit: 50,
          truncated: true,
        }),
      ),
    )
    renderPage()

    expect(await screen.findByText(/120 件ありますが/)).toBeInTheDocument()
  })

  /**
   * 朝いちばんに開いて最初に目に入るのが、もう出てしまった船では一覧が信用されない。
   * 既定は本日以降に絞り、過去を見たいときだけ明示的に外す。
   */
  describe('出港済みの扱い', () => {
    function captureRequests() {
      const urls: string[] = []
      server.use(
        http.get(API_PATHS.voyages, ({ request }) => {
          urls.push(request.url)
          return HttpResponse.json({ voyages: [VOYAGE], totalCount: 1, limit: 50, truncated: false })
        }),
      )
      return urls
    }

    it('既定では本日以降の出発だけを取りに行く', async () => {
      const urls = captureRequests()
      renderPage()

      await waitFor(() => expect(urls.length).toBeGreaterThan(0))
      const params = new URL(urls[0]).searchParams
      // サーバは日時（Instant）で受け取る。日付のまま送ると解釈できない
      expect(params.get('departureFrom')).toBe(
        new Date(`${businessToday()}T00:00:00+09:00`).toISOString(),
      )
    })

    it('「出港済みも含める」を選ぶと期間の下限が外れる', async () => {
      const urls = captureRequests()
      renderPage()
      await waitFor(() => expect(urls.length).toBeGreaterThan(0))

      await userEvent.click(screen.getByLabelText('出港済みも含める'))
      await userEvent.click(screen.getByRole('button', { name: '検索する' }))

      await waitFor(() => expect(urls.length).toBeGreaterThan(1))
      const params = new URL(urls[urls.length - 1]).searchParams
      expect(params.get('departureFrom')).toBeNull()
    })

    /** 日付をそのまま送ると、実バックエンドは日時として解釈できず断る。 */
    it('入力した出発日は日時に変換して送る', async () => {
      const urls = captureRequests()
      renderPage()
      await waitFor(() => expect(urls.length).toBeGreaterThan(0))

      await userEvent.type(screen.getByLabelText('出発日（この日まで）'), '2026-12-31')
      await userEvent.click(screen.getByRole('button', { name: '検索する' }))

      await waitFor(() => expect(urls.length).toBeGreaterThan(1))
      const params = new URL(urls[urls.length - 1]).searchParams
      // その日の終わりはミリ秒の端まで含める。サーバ側（経路候補の期限）と端をそろえないと、
      // 1 秒の間に出る便だけが片方で落ちる
      expect(params.get('departureTo')).toBe(
        new Date('2026-12-31T23:59:59.999+09:00').toISOString(),
      )
    })
  })

  /** 番号を打ち直させると、打ち間違いで別の航海ができる。 */
  it('更新の導線は航海番号を引き継ぐ', async () => {
    renderPage()

    await waitFor(() =>
      expect(screen.getByRole('link', { name: '更新する' })).toHaveAttribute(
        'href',
        '/routing/voyages/new?voyageNumber=V0100',
      ),
    )
  })

  /**
   * 一覧 → 詳細の到達性（#552 / IT4）。
   *
   * 一覧は出発地と目的地しか見せない。途中の寄港地と区間ごとの時刻を確かめる入口が
   * ここに無いと、詳細画面は作っても誰にも踏まれない。
   */
  it('航海番号から詳細へ行ける', async () => {
    renderPage()

    expect(await screen.findByRole('link', { name: 'V0100' })).toHaveAttribute(
      'href',
      '/routing/voyages/V0100',
    )
  })
})