import { describe, expect, it } from 'vitest'
import { ROLES, type Role } from '../../types/role'
import { NAVIGATION } from '../navigation'

describe('ナビゲーション定義', () => {
  /**
   * **`available: false` のまま残った項目を落とす**（IT12 デモ 12）。
   *
   * 準備中の印は、その画面を作ったら外すためにある。外し忘れると、**画面はあるのに
   * navbar からもダッシュボードからも到達できない**——ルートガードの検査では
   * 捕まえられない（ガードは URL を直接叩けば通る）。
   *
   * 次に「先に定義だけ置く」項目を足すときは、ここに理由つきで名前を書く。
   */
  it('準備中のまま残っている項目が無い', () => {
    const pending = NAVIGATION.filter((item) => !item.available).map((item) => item.to)

    expect(
      pending,
      '画面を作ったのに準備中の印が残っている。navbar にもダッシュボードにも出ない',
    ).toEqual([])
  })

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

  /**
   * 入口を持たないことが決定である場合だけ、ここに理由とともに並べる。
   *
   * 「まだ作っていない」と「開かないと決めた」は違う。並べたまま放置されないよう、
   * 解消する時期を書く。
   */
  const ROLES_WITHOUT_OWN_MENU: Record<string, string> = {}

  it('業務ロールには担当業務への入口が少なくとも 1 つある', () => {
    const businessRoles = ROLES.filter((role) => ROLES_WITHOUT_OWN_MENU[role] === undefined)

    for (const role of businessRoles) {
      const own = NAVIGATION.filter((item) => item.roles.includes(role as Role))

      expect(own, `${role} に担当業務への入口がない`).not.toHaveLength(0)
    }
  })

  it('入口を持たないと決めたロールは、実際に持っていない', () => {
    // 理由を書いたまま入口が増えると、免除の一覧が事実と合わなくなる
    for (const role of Object.keys(ROLES_WITHOUT_OWN_MENU)) {
      const own = NAVIGATION.filter((item) => item.roles.includes(role as Role))

      expect(own, `${role} は入口を持たない前提だが ${own.length} 件ある`).toHaveLength(0)
    }
  })

  it('荷主には自社貨物一覧への専用入口がある', () => {
    const own = NAVIGATION.filter((item) => item.roles.includes('ROLE_SHIPPER'))

    expect(own.map((item) => item.to)).toContain('/shipper/tracking')
    expect(own.map((item) => item.label)).toContain('自分の貨物')
  })
})
