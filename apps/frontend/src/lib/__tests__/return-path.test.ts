import { describe, expect, it } from 'vitest'
import { safeReturnPath, withReturnTo } from '../return-path'

describe('戻り先のパス', () => {
  it('アプリ内の絶対パスは受け入れる', () => {
    expect(safeReturnPath('/routing/design/BKG-1?deadline=2026-09-30')).toBe(
      '/routing/design/BKG-1?deadline=2026-09-30',
    )
  })

  it.each([
    '//evil.example.com',
    // ブラウザは `/\` を `//` と同じに扱う。ここを漏らすと外部へ飛べる
    '/\\evil.example.com',
    'https://evil.example.com',
    'routing/design/BKG-1',
    null,
  ])(
    '外部へ出る値は受け入れない: %s',
    (value) => {
      // URL の値をそのまま遷移先にすると、外部のアドレスを差し込まれる余地ができる
      expect(safeReturnPath(value)).toBeNull()
    },
  )

  it('戻り先は条件ごと持ち回る', () => {
    // 条件を落とすと、戻ったときに入れ直すことになる
    expect(withReturnTo('/routing/voyages/V0100', '/routing/design/BKG-1?deadline=2026-09-30')).toBe(
      '/routing/voyages/V0100?from=%2Frouting%2Fdesign%2FBKG-1%3Fdeadline%3D2026-09-30',
    )
  })
})
