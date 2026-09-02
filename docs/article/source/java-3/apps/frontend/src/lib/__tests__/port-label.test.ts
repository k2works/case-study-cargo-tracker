import { describe, expect, it } from 'vitest'

import { portLabel } from '../port-label'

describe('portLabel', () => {
  it('名前が引けたら「名前（符号）」で出す', () => {
    expect(portLabel('SGSIN', 'Singapore', '―')).toBe('Singapore（SGSIN）')
  })

  /**
   * **名前が引けなくても符号は出す。** 誤配した港は地点マスタに無いことがある。
   * 名前を条件に落とすと、最も異常な誤配ほど画面から消える。
   */
  it('名前が引けなければ符号だけを出す', () => {
    expect(portLabel('XXUNK', null, '―')).toBe('XXUNK')
    expect(portLabel('XXUNK', undefined, '―')).toBe('XXUNK')
  })

  it('符号も無ければ、渡した代わりの文字列を出す', () => {
    expect(portLabel(null, 'Singapore', '（荷役の記録がありません）')).toBe(
      '（荷役の記録がありません）',
    )
  })
})
