import { describe, expect, it } from 'vitest';
import { HandlingValidationError } from './handling-validation-error.js';
import { HandlingActivity } from './handling-activity.js';
import type { CargoSnapshot } from './cargo-snapshot.js';

/** JPTYO --V001--> HKHKG --V002--> USLAX の 2 区間旅程スナップショット */
function snapshot(): CargoSnapshot {
  return {
    bookingId: 'bk-1',
    origin: 'JPTYO',
    destination: 'USLAX',
    routingStatus: 'ROUTED',
    itineraryLegs: [
      { loadLocation: 'JPTYO', unloadLocation: 'HKHKG', voyageNumber: 'V001' },
      { loadLocation: 'HKHKG', unloadLocation: 'USLAX', voyageNumber: 'V002' },
    ],
  };
}

function activity(params: { type: string; location: string; voyageNumber?: string }): HandlingActivity {
  return HandlingActivity.register({
    bookingId: 'bk-1',
    type: params.type,
    location: params.location,
    completionTime: new Date('2026-09-28T10:00:00Z'),
    voyageNumber: params.voyageNumber,
    operatorName: '作業員A',
  });
}

describe('HandlingActivity.isValidFor（荷役妥当性デシジョンテーブル）', () => {
  it.each([
    // [説明, type, location, voyage, expected]
    ['RECEIVE: 出発港一致で有効', 'RECEIVE', 'JPTYO', undefined, true],
    ['RECEIVE: 出発港不一致で無効（警告）', 'RECEIVE', 'HKHKG', undefined, false],
    ['LOAD: 積込港・航海一致で有効', 'LOAD', 'JPTYO', 'V001', true],
    ['LOAD: 第 2 区間の積込港一致で有効', 'LOAD', 'HKHKG', 'V002', true],
    ['LOAD: 積込港不一致で無効（MISROUTED）', 'LOAD', 'USLAX', 'V001', false],
    ['LOAD: 港一致でも航海不一致で無効（MISROUTED）', 'LOAD', 'JPTYO', 'V999', false],
    ['UNLOAD: 荷降港・航海一致で有効', 'UNLOAD', 'HKHKG', 'V001', true],
    ['UNLOAD: 荷降港不一致で無効（MISROUTED）', 'UNLOAD', 'JPTYO', 'V001', false],
    ['CLAIM: 目的港一致で有効', 'CLAIM', 'USLAX', undefined, true],
    ['CLAIM: 目的港不一致で無効（警告）', 'CLAIM', 'JPTYO', undefined, false],
    ['CUSTOMS: 場所チェックなしで常に有効', 'CUSTOMS', 'HKHKG', undefined, true],
  ])('%s', (_desc, type, location, voyageNumber, expected) => {
    expect(activity({ type, location, voyageNumber }).isValidFor(snapshot())).toBe(expected);
  });

  it('旅程が空のスナップショットでは LOAD は無効', () => {
    const empty: CargoSnapshot = { ...snapshot(), itineraryLegs: [] };
    expect(activity({ type: 'LOAD', location: 'JPTYO', voyageNumber: 'V001' }).isValidFor(empty)).toBe(false);
  });
});

describe('HandlingActivity.register（登録時の不変条件）', () => {
  it.each([
    ['LOAD', 'V001'],
    ['UNLOAD', 'V001'],
  ])('%s は VoyageNumber 必須（未指定はエラー）', (type) => {
    expect(() => activity({ type, location: 'JPTYO' })).toThrow(HandlingValidationError);
  });

  it('RECEIVE は VoyageNumber 不要で登録できる', () => {
    const a = activity({ type: 'RECEIVE', location: 'JPTYO' });
    expect(a.voyageNumber).toBeNull();
    expect(a.type.value).toBe('RECEIVE');
  });

  it('場所は UN/LOCODE 形式で検証される', () => {
    expect(() => activity({ type: 'RECEIVE', location: 'bad' })).toThrow();
  });

  it('bookingId は必須', () => {
    expect(() =>
      HandlingActivity.register({
        bookingId: ' ',
        type: 'RECEIVE',
        location: 'JPTYO',
        completionTime: new Date(),
      }),
    ).toThrow(HandlingValidationError);
  });

  describe('未来日ガード（IT7 1.4）', () => {
    const now = new Date('2026-09-10T00:00:00Z');
    const params = (completionTime: Date) => ({
      bookingId: 'bk-1',
      type: 'RECEIVE',
      location: 'JPTYO',
      completionTime,
    });

    it.each([
      ['現在時刻ちょうどは許可', new Date('2026-09-10T00:00:00Z'), false],
      ['1 分過去は許可', new Date('2026-09-09T23:59:00Z'), false],
      ['1 分未来は拒否', new Date('2026-09-10T00:01:00Z'), true],
    ])('register: %s', (_label, completionTime, shouldThrow) => {
      const run = () => HandlingActivity.register(params(completionTime), now);
      if (shouldThrow) {
        expect(run).toThrow(HandlingValidationError);
      } else {
        expect(run).not.toThrow();
      }
    });

    it('now 未指定なら未来日でもスキップ（再構築・単体互換）', () => {
      expect(() => HandlingActivity.register(params(new Date('2099-01-01T00:00:00Z')))).not.toThrow();
    });
  });
});
