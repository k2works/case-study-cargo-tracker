import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { NAVIGATION } from '../navigation'

/**
 * ユーザーマニュアルの業務フロー章が、実際のメニューを覆っているかを検査する。
 *
 * <p>読者は「自分の仕事がシステムのどこにあたるか」から探すため、業務フロー章が入口になる。
 * 画面を追加したのに対応表を更新しないと、使えるようになった画面を誰も探しに来ない。
 */
// vitest の実行時カレントは apps/frontend
const MANUAL = readFileSync(resolve('../../docs/manual/01-業務フロー.md'), 'utf8')

describe('業務フロー章とメニューの整合', () => {
  it('すべてのメニューが業務フロー章に載っている', () => {
    // ダッシュボードは工程ではなく入口なので、対応表の対象外
    const missing = NAVIGATION.filter(
      (item) => item.label !== 'ダッシュボード' && !MANUAL.includes(item.label),
    ).map((item) => item.label)

    expect(missing, '業務フロー章の対応表に無い画面').toEqual([])
  })

  it('使える画面が「準備中」と書かれていない', () => {
    // 実装したのに「準備中」のままだと、利用者はその画面を開こうとしない
    for (const item of NAVIGATION.filter((i) => i.available && i.label !== 'ダッシュボード')) {
      const line = MANUAL.split('\n').find(
        (l) => l.includes(item.label) && l.startsWith('|'),
      )
      expect(line, `${item.label} の行が対応表に無い`).toBeDefined()
      expect(line, `${item.label} は使えるのに「準備中」と書かれている`).not.toContain('準備中')
    }
  })
})
