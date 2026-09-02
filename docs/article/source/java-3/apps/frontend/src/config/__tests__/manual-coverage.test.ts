import { readdirSync, readFileSync } from 'node:fs'
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
const MANUAL_INDEX = readFileSync(resolve('../../docs/manual/index.md'), 'utf8')
const MKDOCS = readFileSync(resolve('../../mkdocs.yml'), 'utf8')

/** `docs/manual/NN-*.md` の章。索引と目次の両方に現れなければならない。 */
const CHAPTERS = readdirSync(resolve('../../docs/manual'))
  .filter((name) => /^\d\d-.+\.md$/.test(name))
  .sort()

/**
 * **章を書くことと、章を届けることは別である。**
 *
 * <p>IT8 で 09 章を書いたのに、索引にも mkdocs のナビにも載せ忘れ、
 * **書いた章がドキュメントサイトから辿れない**状態になった。本文だけを完了条件に
 * していると、この 3 点（索引・ナビ・キャプチャ）が同時に落ちる。
 */
describe('章が索引と目次から辿れる', () => {
  it('章が 1 つも読み取れていなければ、この検査は何も守らない', () => {
    expect(CHAPTERS.length).toBeGreaterThan(5)
  })

  it('すべての章が索引に載っている', () => {
    const missing = CHAPTERS.filter((chapter) => !MANUAL_INDEX.includes(chapter))

    expect(missing, '索引（docs/manual/index.md）に載っていない章').toEqual([])
  })

  it('すべての章がドキュメントサイトの目次に載っている', () => {
    const missing = CHAPTERS.filter((chapter) => !MKDOCS.includes(`manual/${chapter}`))

    expect(missing, 'mkdocs.yml のナビに載っていない章。書いても誰も辿れない').toEqual([])
  })
})

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
