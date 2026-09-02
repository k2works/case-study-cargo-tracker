import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 廃止した名前が設計・計画・運用の文書へ戻っていないことを検査する。
 *
 * <p>ADR で「廃止する」と決めても、決定を検査に落とさなければ守られない。IT6 では
 * ADR-022 が「設計との食い違いを寄せる」と宣言したのに、寄せたのは 1 箇所だけで
 * **8 箇所が旧名のまま残っていた**。設計書を写経する人はそちらを読むため、廃止した
 * 名前がそのまま実装に入る。
 *
 * <p>`docs/article/` は連載記事（別プロジェクトの題材）なので対象外とする。
 */
const RETIRED_NAMES: Record<string, string> = {
  // ADR-022 決定 1: 採番が bookingms である以上、「割り当てを依頼する」イベントは要らない
  CargoBookedEvent: 'ADR-022 決定 1 で廃止。TrackingNumberIssuedEvent を使う',
}

const TARGET_DIRS = ['docs/design', 'docs/development', 'docs/requirements', 'docs/operation']

/** 廃止を説明している行（打ち消し線・ADR への言及）は残ってよい。 */
function isExplanation(line: string): boolean {
  return line.includes('廃止') || line.includes('ADR-022') || line.includes('022-domain')
}

function markdownFilesIn(dir: string): string[] {
  const root = join(process.cwd(), '..', '..', dir)
  const found: string[] = []
  for (const entry of readdirSync(root)) {
    const path = join(root, entry)
    if (statSync(path).isDirectory()) {
      continue
    }
    if (entry.endsWith('.md')) {
      found.push(path)
    }
  }
  return found
}

describe('廃止した名前が文書へ戻っていない', () => {
  for (const [name, reason] of Object.entries(RETIRED_NAMES)) {
    it(`${name} が説明以外の文脈で残っていない（${reason}）`, () => {
      const offenders: string[] = []
      for (const dir of TARGET_DIRS) {
        for (const file of markdownFilesIn(dir)) {
          readFileSync(file, 'utf-8')
            .split('\n')
            .forEach((line, index) => {
              if (line.includes(name) && !isExplanation(line)) {
                offenders.push(`${file}:${index + 1}`)
              }
            })
        }
      }

      expect(offenders, `${name} は ${reason}`).toEqual([])
    })
  }
})
