import { describe, expect, it } from 'vitest'
import { ROLES, type Role } from '../../types/role'
import { NAVIGATION } from '../navigation'

describe('ナビゲーション定義', () => {
  it('遷移先が重複していない', () => {
    const paths = NAVIGATION.map((item) => item.to)

    expect(new Set(paths).size).toBe(paths.length)
  })

  it('定義されていないロールを参照していない', () => {
    const unknown = NAVIGATION.flatMap((item) => item.roles).filter(
      (role) => !ROLES.includes(role),
    )

    expect(unknown).toEqual([])
  })

  it('どのロールにもダッシュボードへの入口がある', () => {
    // ロール別の到達性を欠くと、画面が受入基準を満たしていても利用者はそこへ行けない
    for (const role of ROLES) {
      const reachable = NAVIGATION.filter(
        (item) => item.roles.length === 0 || item.roles.includes(role as Role),
      )

      expect(reachable.map((item) => item.to)).toContain('/dashboard')
    }
  })

  it('業務ロールには担当業務への入口が少なくとも 1 つある', () => {
    // ROLE_ADMIN は運用管理者であり、業務メニューを持たない（全ロール共通のみ）
    const businessRoles = ROLES.filter((role) => role !== 'ROLE_ADMIN')

    for (const role of businessRoles) {
      const own = NAVIGATION.filter((item) => item.roles.includes(role as Role))

      expect(own, `${role} に担当業務への入口がない`).not.toHaveLength(0)
    }
  })
})
