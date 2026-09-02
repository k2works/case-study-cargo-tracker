import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * ADR-013 の依存の向きを固定する。
 *
 * 規則は、同じ変更で検査に落とさなければ守られない。`features/` が `pages/` を参照し始めると
 * ルーティングと業務実装が絡み、画面の付け替えができなくなる。
 */

const FEATURES_DIR = join(__dirname, '..')

function sourceFilesUnder(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) {
      return entry === '__tests__' ? [] : sourceFilesUnder(path)
    }
    return path.endsWith('.ts') || path.endsWith('.tsx') ? [path] : []
  })
}

describe('フロントエンドの依存の向き（ADR-013）', () => {
  const files = sourceFilesUnder(FEATURES_DIR)

  it('検査対象のファイルが集まっている', () => {
    // 0 件なら、この検査は何も守らないまま緑になる
    expect(files.length).toBeGreaterThan(0)
  })

  it.each(files)('%s は pages/ を参照しない', (file) => {
    const source = readFileSync(file, 'utf8')
    const offending = [...source.matchAll(/from\s+['"]([^'"]+)['"]/g)]
      .map((match) => match[1])
      .filter((specifier) => /(^|\/)pages\//.test(specifier))

    expect(offending).toEqual([])
  })
})
