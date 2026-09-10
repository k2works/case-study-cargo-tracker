import { describe, expect, it } from 'vitest';
import {
  formatPortDateTime,
  portLocalToInstant,
  portTimeZone,
  portsWithTimeZone,
} from './portTimeZone';

/**
 * 荷役の日時は港のローカル時刻（non_functional.md:212 / IT9 引き継ぎ H.7）。
 *
 * <p><b>3 IT 繰り越した負債である。</b> 業務タイムゾーン固定だと、海外港で
 * 最大 13 時間ずれる——現場が「08:30 に降ろした」と入れたのに、履歴には
 * 21:30 と出る。</p>
 */
describe('港のタイムゾーン', () => {
  it('対応表が空でない（検査が空振りしていない）', () => {
    expect(portsWithTimeZone().length).toBeGreaterThanOrEqual(5);
  });

  it('対応表のタイムゾーンがすべて実在する（打ち間違いを黙って JST にしない）', () => {
    // **名簿と突き合わせる正典は無い**（港は利用者が打ち込んで増える）。
    // 代わりに、書いた値そのものが解釈できるかを見る——`Asia/Tokio` のような
    // 打ち間違いは実行時に例外になるか、黙って既定に倒れる（IT12 レビュー 高）。
    for (const port of portsWithTimeZone()) {
      const zone = portTimeZone(port);
      expect(() => new Intl.DateTimeFormat('ja-JP', { timeZone: zone }), `${port} → ${zone}`)
        .not.toThrow();
      // 業務タイムゾーンを書いた港は「同じ地域」を意味する。打ち間違いが
      // 既定と同じ文字列になることは無いので、ここでは区別しない。
      expect(zone).not.toBe('');
    }
  });

  it('知らない港は業務タイムゾーンで扱う（画面には併記があるので読める）', () => {
    expect(portTimeZone('ZZZZZ')).toBe('Asia/Tokyo');
    expect(portTimeZone(null)).toBe('Asia/Tokyo');
  });

  it('港ごとに違う時間帯を返す', () => {
    expect(portTimeZone('JPTYO')).toBe('Asia/Tokyo');
    expect(portTimeZone('USNYC')).toBe('America/New_York');
    expect(portTimeZone('SGSIN')).toBe('Asia/Singapore');
  });

  it('小文字で書いても引ける（現場は小文字で打つ）', () => {
    expect(portTimeZone('usnyc')).toBe('America/New_York');
  });
});

describe('荷役の日時の表示', () => {
  it('港のローカル時刻で出し、JST を併記する', () => {
    // 2026-09-16T12:30Z は ニューヨーク 08:30（夏時間）、東京 21:30。
    // **JST 固定だと「21:30 に降ろした」と読める**——13 時間のずれである。
    const shown = formatPortDateTime('2026-09-16T12:30:00Z', 'USNYC');

    expect(shown).toContain('08:30');
    expect(shown).toContain('JST');
    expect(shown).toContain('21:30');
  });

  it('冬は夏時間の分だけ違う（固定のずれを書いていない）', () => {
    // 2026-01-16T13:30Z は ニューヨーク 08:30（冬時間）。
    expect(formatPortDateTime('2026-01-16T13:30:00Z', 'USNYC')).toContain('08:30');
  });

  it('同じ時間帯の港では併記しない（同じ数字を 2 度並べない）', () => {
    const shown = formatPortDateTime('2026-09-16T12:30:00Z', 'JPTYO');

    expect(shown).toContain('21:30');
    expect(shown).not.toContain('JST');
  });

  it('読めない値はそのまま返す（Invalid Date より分かりやすい）', () => {
    expect(formatPortDateTime('こわれた', 'USNYC')).toBe('こわれた');
  });
});

describe('荷役の日時の入力', () => {
  it('入れた時刻はその港の時刻として送る', () => {
    // ニューヨークで 08:30 と入れたら、UTC では 12:30（夏時間）。
    expect(portLocalToInstant('2026-09-16T08:30', 'USNYC'))
      .toBe('2026-09-16T12:30:00Z');
  });

  it('冬時間の日は 1 時間違う（固定のずれを書いていない）', () => {
    expect(portLocalToInstant('2026-01-16T08:30', 'USNYC'))
      .toBe('2026-01-16T13:30:00Z');
  });

  it('日本の港は業務タイムゾーンと同じ結果になる', () => {
    expect(portLocalToInstant('2026-09-16T17:30', 'JPTYO'))
      .toBe('2026-09-16T08:30:00Z');
  });

  it('知らない港は業務タイムゾーンで読む', () => {
    expect(portLocalToInstant('2026-09-16T17:30', 'ZZZZZ'))
      .toBe('2026-09-16T08:30:00Z');
  });

  it('空欄は空欄のまま（「いま」で記録する経路を潰さない）', () => {
    expect(portLocalToInstant('', 'USNYC')).toBe('');
  });
});
