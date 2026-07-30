import { describe, expect, it } from 'vitest';
import { TrackingActivity } from './tracking-activity.js';
import { TrackingExceptionEvent } from './tracking-exception.js';
import { TrackingStatus } from './tracking-status.js';
import { TrackingValidationError } from './tracking-validation-error.js';

function activity(): TrackingActivity {
  return TrackingActivity.create('TRK-0001', 'bk-1');
}

/** 発生前状態を LOADED にした集約（RECEIVE→LOAD 済み）を用意する */
function loadedActivity(): TrackingActivity {
  const tracking = activity();
  tracking.addEvent({ eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('2026-09-01T08:00:00Z'), voyageNumber: null });
  tracking.addEvent({ eventType: 'LOAD', location: 'JPTYO', completionTime: new Date('2026-09-01T10:00:00Z'), voyageNumber: 'V001' });
  return tracking;
}

function occurredAt(): Date {
  return new Date('2026-09-05T10:00:00Z');
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

  it.each([
    ['DEPARTURE', TrackingStatus.ONBOARD_CARRIER],
    ['ARRIVAL', TrackingStatus.AWAITING_CLAIM],
  ])('手動更新向けの %s イベントで状態が %s になる（US17: 荷役で捕捉できない出港・入港）', (eventType, expected) => {
    const tracking = activity();
    tracking.addEvent({ eventType, location: 'JPTYO', completionTime: new Date('2026-09-05T10:00:00Z'), voyageNumber: 'V001' });
    expect(tracking.currentStatus()).toBe(expected);
  });

  it('不正な UN/LOCODE の場所はエラー', () => {
    expect(() =>
      activity().addEvent({ eventType: 'RECEIVE', location: 'bad', completionTime: new Date(), voyageNumber: null }),
    ).toThrow(TrackingValidationError);
  });

  it('不正な完了日時（Invalid Date）はエラー（冪等判定の NaN 破綻を防ぐ）', () => {
    expect(() =>
      activity().addEvent({ eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('bad'), voyageNumber: null }),
    ).toThrow(TrackingValidationError);
  });
});

describe('TrackingActivity 例外処理（US19/US20）', () => {
  it('例外を登録すると currentStatus が EXCEPTION になり発生前状態を封入する', () => {
    const tracking = loadedActivity();
    const added = tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: '悪天候' });
    expect(added).not.toBeNull();
    expect(added!.statusBeforeException).toBe(TrackingStatus.LOADED);
    expect(added!.escalationFlag).toBe(false);
    expect(tracking.currentStatus()).toBe(TrackingStatus.EXCEPTION);
    expect(tracking.hasActiveException()).toBe(true);
  });

  it('LOST 例外は escalationFlag が true になる（US20-3）', () => {
    const tracking = loadedActivity();
    const added = tracking.addException({ exceptionType: 'LOST', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    expect(added!.escalationFlag).toBe(true);
  });

  it('未解決の同種例外があれば追加しない（冪等）', () => {
    const tracking = loadedActivity();
    expect(tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null })).not.toBeNull();
    expect(tracking.addException({ exceptionType: 'DELAY', location: 'USLAX', occurredAt: occurredAt(), description: null })).toBeNull();
    expect(tracking.exceptions).toHaveLength(1);
  });

  it('異種の例外は併存できる', () => {
    const tracking = loadedActivity();
    tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    tracking.addException({ exceptionType: 'DAMAGE', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    expect(tracking.exceptions).toHaveLength(2);
  });

  it('解決すると発生前状態へ復帰する（US19-解決）', () => {
    const tracking = loadedActivity();
    const added = tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    added!.assignId(1);
    tracking.resolveException(1, '代替便を手配');
    expect(tracking.currentStatus()).toBe(TrackingStatus.LOADED);
    expect(tracking.hasActiveException()).toBe(false);
  });

  it('複数例外の併存時は全て解決するまで EXCEPTION を維持する', () => {
    const tracking = loadedActivity();
    const delay = tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    const damage = tracking.addException({ exceptionType: 'DAMAGE', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    delay!.assignId(1);
    damage!.assignId(2);
    tracking.resolveException(1, null);
    expect(tracking.currentStatus()).toBe(TrackingStatus.EXCEPTION); // 破損が未解決
    tracking.resolveException(2, null);
    expect(tracking.currentStatus()).toBe(TrackingStatus.LOADED);
  });

  it('EXCEPTION 中にイベントを追加しても表示状態は EXCEPTION を維持する', () => {
    const tracking = loadedActivity();
    tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    tracking.addEvent({ eventType: 'UNLOAD', location: 'USLAX', completionTime: new Date('2026-09-06T10:00:00Z'), voyageNumber: 'V001' });
    expect(tracking.currentStatus()).toBe(TrackingStatus.EXCEPTION);
  });

  it('対応報告を記録すると reportedAt が設定される', () => {
    const tracking = loadedActivity();
    const added = tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    added!.assignId(1);
    tracking.reportException(1, { newEstimatedArrival: new Date('2026-09-20T00:00:00Z'), notes: '代替ルートで対応' });
    expect(added!.isReported).toBe(true);
    expect(added!.reportNotes).toBe('代替ルートで対応');
  });

  it('EXCEPTION 中に到着した荷降しイベントは解決後も失われず UNLOADED へ復帰する', () => {
    const tracking = loadedActivity(); // 発生前状態は LOADED
    const added = tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    added!.assignId(1);
    // 例外対応中に荷降しが到着する
    tracking.addEvent({ eventType: 'UNLOAD', location: 'USLAX', completionTime: new Date('2026-09-06T10:00:00Z'), voyageNumber: 'V001' });
    tracking.resolveException(1, null);
    // 発生前状態（LOADED）ではなく、例外中に進んだ UNLOADED へ復帰する
    expect(tracking.currentStatus()).toBe(TrackingStatus.UNLOADED);
  });

  it('発生前状態が異なる複数例外を解決したときの復帰先が決定的（同時刻は id で決定化）', () => {
    const resolvedAt = new Date('2026-09-10T00:00:00Z');
    const reconstructResolved = (id: number, exceptionType: string, before: TrackingStatus): TrackingExceptionEvent =>
      TrackingExceptionEvent.reconstruct({
        id,
        exceptionType: exceptionType as never,
        location: 'JPTYO',
        occurredAt: occurredAt(),
        description: null,
        escalationFlag: false,
        statusBeforeException: before,
        resolvedAt,
        resolutionNotes: null,
        reportedAt: null,
        newEstimatedArrival: null,
        reportNotes: null,
      });
    const tracking = TrackingActivity.reconstruct({
      id: 1,
      trackingNumber: 'TRK-0001',
      bookingId: 'bk-1',
      events: [{ eventType: 'RECEIVE', location: 'JPTYO', completionTime: new Date('2026-09-01T08:00:00Z'), voyageNumber: null }],
      exceptions: [
        reconstructResolved(1, 'DELAY', TrackingStatus.NOT_RECEIVED),
        reconstructResolved(2, 'DAMAGE', TrackingStatus.RECEIVED),
      ],
    });
    // 同時刻解決なので id 最大（2, 発生前 RECEIVED）が最後に解決した例外。イベント由来（RECEIVED）と比較し進んだ方へ復帰
    expect(tracking.currentStatus()).toBe(TrackingStatus.RECEIVED);
  });

  it('解決済みの例外への対応報告は拒否される', () => {
    const tracking = loadedActivity();
    const added = tracking.addException({ exceptionType: 'DELAY', location: 'JPTYO', occurredAt: occurredAt(), description: null });
    added!.assignId(1);
    tracking.resolveException(1, null);
    expect(() =>
      tracking.reportException(1, { newEstimatedArrival: null, notes: '再対応' }),
    ).toThrow(TrackingValidationError);
  });

  it('不正な例外種別はエラー', () => {
    expect(() =>
      activity().addException({ exceptionType: 'BAD', location: 'JPTYO', occurredAt: occurredAt(), description: null }),
    ).toThrow(TrackingValidationError);
  });

  it('存在しない例外の解決・報告はエラー', () => {
    const tracking = loadedActivity();
    expect(() => tracking.resolveException(999, null)).toThrow(TrackingValidationError);
    expect(() => tracking.reportException(999, { newEstimatedArrival: null, notes: 'x' })).toThrow(TrackingValidationError);
  });
});
