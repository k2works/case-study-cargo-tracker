import { describe, expect, it } from 'vitest'
import { PANELS } from '../dashboard-panels'
import { ROLES } from '../../types/role'

/**
 * ダッシュボードの導線（ロール別の入口）。
 *
 * navbar には「遷移先が重複していない」検査があったが、**ダッシュボードには無かった**。
 * IT9 で追跡管理者のパネルに同じ行き先のリンクが 2 つ並び、そのまま通った——
 * 利用者から見れば、押す前にどちらが正しいか分からない 2 択が増えただけである。
 */
describe('ダッシュボードのパネル', () => {
  it('同じパネルの中に、同じ行き先が 2 つ無い', () => {
    for (const panel of PANELS) {
      const paths = panel.actions.map((action) => action.to)

      expect(
        new Set(paths).size,
        `${panel.title} に同じ行き先のリンクが 2 つある: ${paths.join(', ')}`,
      ).toBe(paths.length)
    }
  })

  it('同じパネルの中に、同じ文言のリンクが 2 つ無い', () => {
    for (const panel of PANELS) {
      const labels = panel.actions.map((action) => action.label)

      expect(new Set(labels).size, `${panel.title} に同じ文言のリンクが 2 つある`).toBe(
        labels.length,
      )
    }
  })

  it('定義されていないロールのパネルが無い', () => {
    const unknown = PANELS.map((panel) => panel.role).filter(
      (role) => !ROLES.includes(role),
    )

    expect(unknown).toEqual([])
  })

  it('パネルが 1 つも読めていない状態で緑にしない', () => {
    expect(PANELS.length).toBeGreaterThan(3)
    expect(PANELS.every((panel) => panel.actions.length > 0)).toBe(true)
  })

  it('荷主ダッシュボードは自社貨物一覧へ案内する', () => {
    const panel = PANELS.find((item) => item.role === 'ROLE_SHIPPER')

    expect(panel?.actions).toContainEqual({
      label: '自分の貨物を見る',
      to: '/shipper/tracking',
    })
  })
})
