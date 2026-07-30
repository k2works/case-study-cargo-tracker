import { describe, expect, it } from 'vitest';
import { TrackingActivity } from './tracking-activity.js';
import { TrackingStatus } from './tracking-status.js';
import { TrackingValidationError } from './tracking-validation-error.js';

function activity(): TrackingActivity {
  return TrackingActivity.create('TRK-0001', 'bk-1');
}

describe('TrackingActivity（追跡レコード集約）', () => {
  it('作成直後の状態は NOT_RECEIVED（受領待ち）', () => {
    expect(activity().currentStatus()).toBe(TrackingStatus.NOT_RECEIVED);
  });

  it.each([
    ['RECEIVE', TrackingStatus.RECEIVED],
    ['LOAD', TrackingStatus.LOADED],
    ['UNLOAD', TrackingStatus.UNLOADED],
    ['CLAIM', TrackingStatus.CLAIMED],
  ])('%s イベント追加で状態が %s になる', (eventType, expected) => {
    const tracking = activity();
    tracking.addEvent({
      eventType,
      location: 'JPTYO',
      completionTime: new Date('2026-09-01T10:00:00Z'),
      voyageNumber: eventType === 'LOAD' || eventType === 'UNLOAD' ? 'V001' : null,
    });
    expect(tracking.currentStatus()).toBe(expected);
  });

  it('CUSTOMS イベントは状態を変えない（直前状態を維持）', () => {
    const tracking = activity();
    tracking.addEvent({ eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('2026-09-01T10:00:00Z'), voyageNumber: null });
    tracking.addEvent({ eventType: 'CUSTOMS', location: 'JPTYO', completionTime: new Date('2026-09-02T10:00:00Z'), voyageNumber: null });
    expect(tracking.currentStatus()).toBe(TrackingStatus.RECEIVED);
  });

  it('イベントは完了時刻の昇順で状態判定される（追加順に依存しない）', () => {
    const tracking = activity();
    tracking.addEvent({ eventType: 'LOAD', location: 'JPTYO', completionTime: new Date('2026-09-02T10:00:00Z'), voyageNumber: 'V001' });
    tracking.addEvent({ eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('2026-09-01T10:00:00Z'), voyageNumber: null });
    expect(tracking.currentStatus()).toBe(TrackingStatus.LOADED);
  });

  it('同一種別・同一時刻のイベントは重複追加されない（冪等）', () => {
    const tracking = activity();
    const event = { eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('2026-09-01T10:00:00Z'), voyageNumber: null };
    expect(tracking.addEvent(event)).toBe(true);
    expect(tracking.addEvent(event)).toBe(false);
    expect(tracking.events).toHaveLength(1);
  });

  it('追跡番号・予約 ID は必須', () => {
    expect(() => TrackingActivity.create(' ', 'bk-1')).toThrow(TrackingValidationError);
    expect(() => TrackingActivity.create('TRK-0001', '')).toThrow(TrackingValidationError);
  });

  it('不正なイベント種別はエラー', () => {
    expect(() =>
      activity().addEvent({ eventType: 'BAD', location: 'JPTYO', completionTime: new Date(), voyageNumber: null }),
    ).toThrow(TrackingValidationError);
  });
});
