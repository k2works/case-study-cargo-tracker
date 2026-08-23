import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { handlingActivities, handlingHandlers } from '../../mocks/handlers/handling'
import { resetTrackings, trackingHandlers } from '../../mocks/handlers/tracking'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { TrackingManagePage } from '../tracking-manage-page'

/**
 * 貨物状態の管理（US17・US19・US20）。**追跡管理者の担当画面**。
 *
 * IT7 までこのロールは荷役の履歴しか見られなかった。ここが担当画面の本体である。
 */
describe('貨物状態の管理（US17・US19・US20）', () => {
  beforeEach(() => {
    handlingActivities.length = 0
    resetTrackings()
    loginAs(['ROLE_TRACKER'])
    server.use(...handlingHandlers, ...trackingHandlers)
  })

  function renderPage() {
    return renderWithProviders(<TrackingManagePage />, ['/tracking/manage'], undefined, {
      path: '/tracking/manage',
    })
  }

  async function show(user: ReturnType<typeof userEvent.setup>) {
    await user.type(await screen.findByLabelText('追跡番号'), 'TRK-20260823-0001')
    await user.click(screen.getByRole('button', { name: '貨物を表示する' }))
    await screen.findByRole('heading', { name: 'TRK-20260823-0001' })
  }

  async function update(user: ReturnType<typeof userEvent.setup>, status: string) {
    await screen.findByRole('option', { name: '受領済み' })
    await user.selectOptions(screen.getByLabelText('新しい状態'), status)
    await user.selectOptions(screen.getByLabelText('現在地'), 'JPTYO')
    await user.type(screen.getByLabelText('日時'), '2027-09-03T09:00')
    await user.click(screen.getByRole('button', { name: '状態を更新する' }))
  }

  /** US17-1・US17-2。 */
  it('追跡番号で貨物を開き、状態を手で反映できる', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)

    await update(user, 'RECEIVED')

    expect(await screen.findByText('更新しました。')).toBeInTheDocument()
  })

  /**
   * [ADR-024] 決定 1。**押せるのに断られる操作を出さない。**
   *
   * 進める先の選択肢はサーバが返す。画面が全状態を並べて 409 を受けるのは、
   * 断られる操作を出していることと同じである。
   */
  it('戻る向きの状態は、選択肢に出ない', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)
    await update(user, 'RECEIVED')
    await screen.findByText('更新しました。')

    // 受領済みになったので、受領待ちへは戻せない
    expect(screen.queryByRole('option', { name: '受領待ち' })).not.toBeInTheDocument()
  })

  /** US19-1・US19-2。 */
  it('遅延を起票すると、状態が「例外発生」になる', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)

    await user.click(screen.getByRole('button', { name: '例外を起票する' }))
    await screen.findByRole('option', { name: '遅延' })
    await user.selectOptions(screen.getByLabelText('例外の種別'), 'DELAY')
    await user.type(screen.getByLabelText('発生状況'), '台風により出港が遅れています')
    await user.click(screen.getByRole('button', { name: '起票する' }))

    expect(await screen.findByText('起票しました。')).toBeInTheDocument()
    expect(await screen.findByText('例外発生')).toBeInTheDocument()
  })

  /**
   * [ADR-024] 決定 11。**選択肢に出さない。**
   *
   * 誤配（US28）と税関保留（US29）は自動で起票される。手で起票できると、
   * 自動検知と人の起票が混ざる。
   */
  it('誤配・税関保留は、起票の選択肢に出ない', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)

    await user.click(screen.getByRole('button', { name: '例外を起票する' }))
    await screen.findByRole('option', { name: '遅延' })

    expect(screen.getByRole('option', { name: '破損' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '紛失' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /誤配/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /税関/ })).not.toBeInTheDocument()
  })

  /**
   * [ADR-024] 決定 3。**対で確かめる。**
   *
   * 「紛失で立つ」だけを見ると、常に真を返す実装でも緑になる。
   */
  it('紛失だけが緊急として扱われ、破損では立たない', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)

    await user.click(screen.getByRole('button', { name: '例外を起票する' }))
    await screen.findByRole('option', { name: '破損' })
    await user.selectOptions(screen.getByLabelText('例外の種別'), 'DAMAGE')
    await user.type(screen.getByLabelText('発生状況'), '外装に破損があります')
    await user.click(screen.getByRole('button', { name: '起票する' }))
    await screen.findByText('起票しました。')

    expect(screen.queryByText('緊急')).not.toBeInTheDocument()

    // 解決してから紛失を起票する（未解決の例外は 1 件まで）
    await user.click(screen.getByRole('button', { name: '解決する' }))
    await user.type(screen.getByLabelText('対応内容'), '再梱包しました')
    await user.click(screen.getByRole('button', { name: '解決を記録する' }))
    await screen.findByText('解決しました。')

    await user.click(await screen.findByRole('button', { name: '例外を起票する' }))
    await screen.findByRole('option', { name: '紛失' })
    await user.selectOptions(screen.getByLabelText('例外の種別'), 'LOST')
    await user.type(screen.getByLabelText('発生状況'), '積替港で所在が確認できません')
    await user.click(screen.getByRole('button', { name: '起票する' }))

    expect(await screen.findByText('緊急')).toBeInTheDocument()
  })

  /**
   * US19-4・[ADR-024] 決定 2。**発生前の状態に戻る。**
   *
   * 受領待ちまで巻き戻らないことを対で見る——「戻る」だけを見ると、初期状態へ
   * 戻す実装でも緑になる。
   */
  it('例外を解決すると、発生前の状態に戻る', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)
    await update(user, 'RECEIVED')
    await screen.findByText('更新しました。')

    await user.click(screen.getByRole('button', { name: '例外を起票する' }))
    await screen.findByRole('option', { name: '遅延' })
    await user.selectOptions(screen.getByLabelText('例外の種別'), 'DELAY')
    await user.type(screen.getByLabelText('発生状況'), '台風により出港が遅れています')
    await user.click(screen.getByRole('button', { name: '起票する' }))
    await screen.findByText('例外発生')

    await user.click(screen.getByRole('button', { name: '解決する' }))
    await user.type(screen.getByLabelText('対応内容'), '別便に振り替えました')
    await user.click(screen.getByRole('button', { name: '解決を記録する' }))
    await screen.findByText('解決しました。')

    // 経過の表にも同じ語が出る。**いまの状態**を見たいので、定義リストの側で見る
    const summary = screen.getByRole('heading', { name: 'TRK-20260823-0001' }).parentElement
    expect(within(summary as HTMLElement).getByText('受領済み')).toBeInTheDocument()
    expect(screen.queryByText('例外発生')).not.toBeInTheDocument()
  })

  /**
   * **例外が解決するまで、状態は動かせない。**
   *
   * 動かせると、解決したときに戻る先が変わってしまう（[ADR-024] 決定 2）。
   */
  it('例外があるあいだは、状態を手で反映できない', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)

    await user.click(screen.getByRole('button', { name: '例外を起票する' }))
    await screen.findByRole('option', { name: '遅延' })
    await user.selectOptions(screen.getByLabelText('例外の種別'), 'DELAY')
    await user.type(screen.getByLabelText('発生状況'), '台風により出港が遅れています')
    await user.click(screen.getByRole('button', { name: '起票する' }))
    await screen.findByText('起票しました。')

    expect(screen.queryByRole('button', { name: '状態を更新する' })).not.toBeInTheDocument()
    // 起票の代わりに、解決だけが出る（多重起票を押せる形にしない）
    expect(screen.queryByRole('button', { name: '例外を起票する' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '解決する' })).toBeInTheDocument()
  })

  /**
   * 横断規約。**件数を出すだけにしない。**
   *
   * 「3 件ある」と分かっても、どの貨物かが分からなければ次にすることが無い。
   */
  it('未解決例外の件数から、一覧へ辿れる', async () => {
    const user = userEvent.setup()
    renderPage()
    await show(user)

    await user.click(screen.getByRole('button', { name: '例外を起票する' }))
    await screen.findByRole('option', { name: '紛失' })
    await user.selectOptions(screen.getByLabelText('例外の種別'), 'LOST')
    await user.type(screen.getByLabelText('発生状況'), '所在が確認できません')
    await user.click(screen.getByRole('button', { name: '起票する' }))
    await screen.findByText('起票しました。')

    expect(await screen.findByText(/未解決の例外が/)).toBeInTheDocument()
    expect(screen.getByText(/緊急 1 件/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '一覧を見る' })).toHaveAttribute(
      'href',
      '/tracking/manage/exceptions',
    )
  })

  it('存在しない追跡番号は理由を出す', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.type(await screen.findByLabelText('追跡番号'), 'TRK-20260823-9999')
    await user.click(screen.getByRole('button', { name: '貨物を表示する' }))

    expect(await screen.findByText(/追跡番号が見つかりません/)).toBeInTheDocument()
  })
})
