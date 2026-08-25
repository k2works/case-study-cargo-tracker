import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 型検査のコマンド。
 *
 * <p><strong>`tsc --noEmit` は何も検査しない。</strong>このプロジェクトの `tsconfig.json` は
 * プロジェクト参照構成（`files: []` + `references`）であり、`--noEmit` はルートの空の
 * ファイル一覧を見て**終了 0 を返す**。未定義の関数を呼んでも通る。
 *
 * <p>IT9 のクローズで実際に踏んだ。**緑を見て安心していた分、検査が無いより危ない。**
 * 型検査は `tsc -b`（= `npm run typecheck`）である。
 *
 * <p>この検査は「使ってはいけないコマンドが、どこにも書かれていない」ことを見る。
 * 手順書やスクリプトに書かれた瞬間、次に読んだ人はそれを信じて使う。
 */
describe('型検査のコマンド', () => {
  const REPO_ROOT = join(import.meta.dirname, '../../../../..')

  /** 検査の対象。**ここが空なら検査は何も守らない**ので、件数も見る。 */
  const TARGETS = [
    'apps/frontend/package.json',
    'package.json',
    '.github/workflows/ci.yml',
    'ops/scripts/develop.js',
    'docs/operation/アプリケーション開発環境セットアップ手順書.md',
  ]

  function read(path: string): string | null {
    try {
      const full = join(REPO_ROOT, path)
      return statSync(full).isFile() ? readFileSync(full, 'utf-8') : null
    } catch {
      return null
    }
  }

  it('検査の対象が読めている（何も守らないまま緑にしない）', () => {
    const found = TARGETS.filter((path) => read(path) !== null)

    expect(found.length, `対象が読めていない: ${TARGETS.join(', ')}`).toBeGreaterThan(3)
  })

  it('`tsc --noEmit` がどこにも書かれていない', () => {
    const offenders = TARGETS.filter((path) => read(path)?.includes('tsc --noEmit') === true)

    expect(
      offenders,
      '`tsc --noEmit` はプロジェクト参照構成では何も検査しない。`tsc -b` を使うこと',
    ).toEqual([])
  })

  it('`npm run typecheck` が `tsc -b` を呼ぶ', () => {
    const packageJson = read('apps/frontend/package.json')

    expect(packageJson, 'package.json が読めない').not.toBeNull()
    const scripts = (JSON.parse(packageJson as string) as { scripts: Record<string, string> })
      .scripts

    expect(scripts.typecheck, '型検査のスクリプトが無い').toBe('tsc -b')
    // build も型検査を含む（CI はこちらを通る）
    expect(scripts.build).toContain('tsc -b')
  })

  /**
   * 運用手順書にも書かれていないこと。
   *
   * <p>手順書は**実行を指示する場所**である。残っていると、次に読んだ人がそれを使う。
   *
   * <p>ふりかえり・ジャーナル・イテレーション計画は対象外にする——そちらは
   * 「**このコマンドは何も検査しない**」と記録している側であり、消してはいけない。
   * <strong>IT6 の計画に既にこの指摘があった</strong>（「プロジェクト参照で素通りしており、
   * モックが型検査されていなかった」）。それでも IT9 で同じ穴に落ちた——
   * **記録では防げず、検査が要る**というのがこの検査の存在理由である。
   */
  it('運用手順書に `tsc --noEmit` が残っていない', () => {
    const operation = join(REPO_ROOT, 'docs/operation')
    const offenders: string[] = []

    for (const entry of readdirSync(operation, { withFileTypes: true })) {
      if (!entry.isFile() || !entry.name.endsWith('.md')) continue
      const full = join(operation, entry.name)
      if (readFileSync(full, 'utf-8').includes('tsc --noEmit')) offenders.push(entry.name)
    }

    expect(offenders.length, '手順書が 1 つも読めていない').toBeGreaterThanOrEqual(0)
    expect(offenders, '`tsc --noEmit` が手順書に残っている。読んだ人がそれを使う').toEqual([])
  })
})
