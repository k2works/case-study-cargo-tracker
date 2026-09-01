import { describe, expect, it } from 'vitest'
import { destinationAfterLogin } from '../destination-after-login'
import type { Role } from '../../../types/role'

/** そのロールを持つ人の判定。**画面と同じ規則を使う**——ここで書き直さない。 */
function holder(roles: Role[]) {
  return (allowed: Role[]) => allowed.length === 0 || allowed.some((r) => roles.includes(r))
}

describe('ログインしたあとの行き先', () => {
  it('行き先が無ければ、ダッシュボードへ', () => {
    expect(destinationAfterLogin(undefined, holder(['ROLE_SALES']))).toBe('/dashboard')
  })

  it('その利用者が開ける画面なら、そこへ戻す', () => {
    expect(destinationAfterLogin('/booking/shippers', holder(['ROLE_SALES']))).toBe(
      '/booking/shippers',
    )
  })

  /**
   * **入った直後に断られない。** 覚えていた行き先は「前に開こうとした人」のもので
   * あり、いま入った人のものではない——利用者を切り替えると、担当外の画面へ
   * 着地して「この操作を行う権限がありません」と出る（利用者からの申告）。
   */
  it('その利用者が開けない画面なら、ダッシュボードへ', () => {
    expect(destinationAfterLogin('/booking/shippers', holder(['ROLE_HANDLER']))).toBe('/dashboard')
  })

  it('下位の画面でも、その上位の担当で判断する', () => {
    expect(destinationAfterLogin('/booking/shippers/new', holder(['ROLE_SALES']))).toBe(
      '/booking/shippers/new',
    )
    expect(destinationAfterLogin('/booking/shippers/new', holder(['ROLE_HANDLER']))).toBe(
      '/dashboard',
    )
  })

  /**
   * **知らない行き先へは送らない。** ナビに無い経路は開けるかどうかを判断できない
   * ——判断できないものへ送ると、また入った直後に断られる。
   */
  it('ナビに無い行き先は、ダッシュボードへ', () => {
    expect(destinationAfterLogin('/403', holder(['ROLE_SALES']))).toBe('/dashboard')
    expect(destinationAfterLogin('/', holder(['ROLE_SALES']))).toBe('/dashboard')
  })
})
