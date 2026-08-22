import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API_PATHS } from '../../config/api'
import { server } from '../../test/msw/server'
import { loginAs, renderWithProviders } from '../../test/render'
import { LockedAccountsPage } from '../locked-accounts-page'

const LOCKED = {
  username: 'sales02',
  displayName: '佐藤花子',
  failedAttempts: 5,
  lockedUntil: '2026-08-22T02:15:00Z',
}

function renderPage() {
  loginAs(['ROLE_ADMIN'])
  return renderWithProviders(<LockedAccountsPage />, ['/admin/accounts'])
}

describe('ロックされたアカウントの解除（US32）', () => {
  it('ロック中のアカウントを、判断に要る項目だけ出す', async () => {
    server.use(http.get(API_PATHS.lockedAccounts, () => HttpResponse.json([LOCKED])))
    renderPage()

    expect(await screen.findByText('sales02')).toBeInTheDocument()
    expect(screen.getByText('佐藤花子')).toBeInTheDocument()
    expect(screen.getByText('5 回')).toBeInTheDocument()
  })

  /** 本人には理由が出ない（US31）ため、管理者が「いま何もない」ことを確かめられる必要がある。 */
  it('ロックされたアカウントが無いときは、その旨を出す', async () => {
    server.use(http.get(API_PATHS.lockedAccounts, () => HttpResponse.json([])))
    renderPage()

    expect(await screen.findByText(/いまロックされているアカウントはありません/))
      .toBeInTheDocument()
  })

  it('解除すると一覧から消える', async () => {
    let unlocked = false
    server.use(
      http.get(API_PATHS.lockedAccounts, () =>
        HttpResponse.json(unlocked ? [] : [LOCKED])),
      // **どのアカウントを解除したか**まで見る。params を無視するハンドラだと、
      // 別人を渡す実装に変えても緑になる
      http.post('/api/v1/admin/accounts/:username/unlock', ({ params }) => {
        if (params.username !== 'sales02') {
          return HttpResponse.json({ message: '指定されたアカウントが見つかりません' },
            { status: 404 })
        }
        unlocked = true
        return HttpResponse.json({ ...LOCKED, failedAttempts: 0, lockedUntil: null })
      }),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '解除する' }))

    expect(await screen.findByText(/いまロックされているアカウントはありません/))
      .toBeInTheDocument()
  })

  /**
   * <strong>失敗回数も戻ることを画面が伝える。</strong>
   *
   * 期限だけを消す仕組みだと、管理者は「解除したのにまたロックされた」に遭う。
   */
  it('失敗回数も 0 に戻ることを伝える', async () => {
    server.use(http.get(API_PATHS.lockedAccounts, () => HttpResponse.json([LOCKED])))
    renderPage()

    expect(await screen.findByText(/失敗回数も 0 に戻り/)).toBeInTheDocument()
  })

  /** US32-3。記録が残ることを管理者が知らないと、記録を頼りにできない。 */
  it('誰が・いつ・どのアカウントを が記録されることを伝える', async () => {
    server.use(http.get(API_PATHS.lockedAccounts, () => HttpResponse.json([LOCKED])))
    renderPage()

    expect(await screen.findByText(/誰が・いつ・どのアカウントを.*記録に残ります/))
      .toBeInTheDocument()
  })
})
