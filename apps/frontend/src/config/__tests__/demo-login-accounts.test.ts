import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { DEMO_ACCOUNTS } from '../demo-login'

/**
 * ログイン画面の「動作確認用の利用者」が、**実際にログインできる利用者と一致する**ことを見る。
 *
 * <p>ここがずれると、利用者は画面に出ている ID を選んで**ログインできない**
 * ——US39 の `sim-shipper01` で実際に起きた（名簿には足したが、種を入れる
 * マイグレーションが環境に届いていなかった）。
 *
 * <p><strong>名簿を書き写さない。</strong>authms の種（`V*__seed*.sql`）から読み取る。
 * 書き写すと、写し忘れがそのまま「検査も知らない利用者」になる。
 */
const AUTH_MIGRATIONS = join(
  process.cwd(),
  '../backend/authms/src/main/resources/db/migration',
)

/** 種の INSERT から利用者名を拾う。 */
function seededUsernames(): Set<string> {
  const names = new Set<string>()
  for (const file of readdirSync(AUTH_MIGRATIONS).filter((name) => name.endsWith('.sql'))) {
    const sql = readFileSync(join(AUTH_MIGRATIONS, file), 'utf-8')
    if (!sql.includes('INSERT INTO users')) {
      continue
    }
    for (const match of sql.matchAll(/\(\s*'([a-z0-9-]+)',\s*'[^']*@/gi)) {
      names.add(match[1])
    }
  }
  return names
}

describe('ログイン画面の動作確認用の利用者', () => {
  it('種を読み取れている（読めていなければ、検査は何も守らない）', () => {
    expect(seededUsernames().size).toBeGreaterThan(5)
  })

  /**
   * <strong>載せたものは、必ず入れる。</strong>画面に出ているのに存在しない ID は、
   * 利用者から見れば「壊れている」と同じである。
   */
  it('一覧の利用者は、すべて種に存在する', () => {
    const seeded = seededUsernames()
    const missing = DEMO_ACCOUNTS.map((account) => account.userId).filter(
      (userId) => !seeded.has(userId),
    )

    expect(missing, 'ログイン画面に出ているが、種に存在しない利用者がある').toEqual([])
  })

  /**
   * <strong>シミュレーションの利用者も載せる。</strong>載せないと、シミュレーションを
   * 流したあと「誰で入れば見えるのか」がシードの SQL を読むまで分からない。
   *
   * <p>ただし<strong>工程を踏む利用者は載せない</strong>——それらは人が使うものではなく、
   * 画面に並べると実業務の利用者と見分けがつかなくなる。載せるのは
   * <strong>結果を確かめるための利用者</strong>だけである。
   */
  it('シミュレーションの結果を確かめる利用者が載っている', () => {
    expect(DEMO_ACCOUNTS.map((account) => account.userId)).toContain('sim-shipper01')
  })
})
