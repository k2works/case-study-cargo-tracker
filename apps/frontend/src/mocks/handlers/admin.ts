/**
 * アカウント管理のモック（US32）。
 *
 * <p>解除の判断に要らないもの（パスワード・メールアドレス）は返さない。本物が返さないものを
 * モックが返すと、画面がそれに依存しても気づけない。
 */
import { HttpResponse, http } from 'msw'
import { API_PATHS } from '../../config/api'
import { lockedAccounts } from '../data'

export const adminHandlers = [
  /**
   * ロックされたアカウント（US32-1）。
   *
   * 本物と同じく<strong>期限切れは含めない</strong>。含めると、管理者は要らない作業をする。
   * パスワードもメールアドレスも返さない（本物が返さないものをモックが返すと、
   * 画面がそれに依存しても気づけない）。
   */
  http.get(API_PATHS.lockedAccounts, () =>
    HttpResponse.json(
      lockedAccounts.filter((account) => new Date(account.lockedUntil) > new Date()),
    ),
  ),

  /** ロックの解除（US32-2）。解除した管理者はサーバが利用者ヘッダから取る。 */
  http.post('/api/v1/admin/accounts/:username/unlock', ({ params }) => {
    const index = lockedAccounts.findIndex((account) => account.username === params.username)
    if (index < 0) {
      return HttpResponse.json({ message: '指定されたアカウントが見つかりません' }, { status: 404 })
    }
    const [removed] = lockedAccounts.splice(index, 1)
    // 失敗回数も 0 に戻す。期限だけ消すと、次の 1 回でまたロックされる
    return HttpResponse.json({ ...removed, failedAttempts: 0, lockedUntil: null })
  }),
]
