import { describe, expect, it } from 'vitest'
import { ROLES } from '../../types/role'
import { DEMO_ACCOUNTS, demoLoginOf } from '../demo-login'

describe('動作確認用ログインの設定', () => {
  it('環境変数で明示的に有効化したときだけ有効になる', () => {
    // 既定を有効にすると、本番でうっかり認証情報が入った画面を出すことになる。
    // 「有効化を書き忘れたら安全側」に倒す
    expect(demoLoginOf(undefined).enabled).toBe(false)
    expect(demoLoginOf('').enabled).toBe(false)
    expect(demoLoginOf('false').enabled).toBe(false)
    expect(demoLoginOf('1').enabled).toBe(false)
    expect(demoLoginOf('true').enabled).toBe(true)
  })

  it('有効なときは利用者 ID とパスワードを持つ', () => {
    const demo = demoLoginOf('true')

    expect(demo.userId).toBe('sales01')
    expect(demo.password).toBe('password')
  })

  it('無効なときは認証情報を持たない', () => {
    const demo = demoLoginOf(undefined)

    expect(demo.userId).toBe('')
    expect(demo.password).toBe('')
  })
})

describe('動作確認用の利用者一覧', () => {
  it('業務ロールをすべて確認できる', () => {
    // 一覧に無いロールは、画面で確かめるたびにシードの SQL を読みに行くことになる
    const covered = new Set(DEMO_ACCOUNTS.flatMap((account) => account.roles))

    for (const role of ROLES.filter((role) => role !== 'ROLE_ADMIN')) {
      expect(covered, `${role} を確認できる利用者が一覧に無い`).toContain(role)
    }
  })

  it('ログインできないアカウントも用意する', () => {
    // 無効化されたアカウントの挙動（US31）は、その利用者がいないと画面から確かめられない
    expect(DEMO_ACCOUNTS.some((account) => !account.canLogIn)).toBe(true)
  })

  it('利用者 ID が重複していない', () => {
    const ids = DEMO_ACCOUNTS.map((account) => account.userId)

    expect(new Set(ids).size).toBe(ids.length)
  })

  it('それぞれが何を確認する利用者かを説明する', () => {
    for (const account of DEMO_ACCOUNTS) {
      expect(account.description, `${account.userId} の説明が無い`).not.toBe('')
    }
  })
})
