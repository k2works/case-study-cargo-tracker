import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HandlingValidationError } from '../../domain/model/handling-validation-error.js';
import { HANDLING_ACTIVITY_REGISTERED_EVENT } from '../../domain/event/handling-activity-registered-event.js';
import {
  CustomsNotClearedError,
  RegisterHandlingActivityService,
  TrackingNumberNotFoundError,
} from './register-handling-activity.service.js';
import type { CargoSnapshot } from '../../domain/model/cargo-snapshot.js';

function snapshot(): CargoSnapshot {
  return {
    bookingId: 'bk-1',
    origin: 'JPTYO',
    destination: 'USLAX',
    routingStatus: 'ROUTED',
    itineraryLegs: [{ loadLocation: 'JPTYO', unloadLocation: 'USLAX', voyageNumber: 'V001' }],
  };
}

describe('RegisterHandlingActivityService（US15/US16）', () => {
  let activities: { save: ReturnType<typeof vi.fn>; findByBookingId: ReturnType<typeof vi.fn> };
  let snapshots: { findByTrackingNumber: ReturnType<typeof vi.fn> };
  let customs: { isCustomsCleared: ReturnType<typeof vi.fn> };
  let events: { emit: ReturnType<typeof vi.fn> };
  let notifier: { notifyStatusChange: ReturnType<typeof vi.fn> };
  let service: RegisterHandlingActivityService;

  beforeEach(() => {
    activities = { save: vi.fn().mockImplementation(async (a) => a), findByBookingId: vi.fn().mockResolvedValue([]) };
    snapshots = { findByTrackingNumber: vi.fn().mockResolvedValue(snapshot()) };
    customs = { isCustomsCleared: vi.fn().mockResolvedValue(false) };
    events = { emit: vi.fn() };
    notifier = { notifyStatusChange: vi.fn() };
    // 未来日ガード（IT7 1.4）の基準時刻。シナリオ日付（2026-09）より後の固定時刻を注入する。
    const testClock = () => new Date('2027-06-01T00:00:00Z');
    service = new RegisterHandlingActivityService(activities, snapshots, customs, events, notifier, testClock);
  });

  function command(overrides: Record<string, unknown> = {}) {
    return {
      trackingNumber: 'TRK-0001',
      type: 'LOAD',
      location: 'JPTYO',
      completionTime: new Date('2026-09-01T10:00:00Z'),
      voyageNumber: 'V001',
      operatorName: '作業員A',
      ...overrides,
    };
  }

  it('追跡番号で貨物を特定し荷役を登録、コミット後にイベントと荷主通知を発行する', async () => {
    const result = await service.register(command());
    expect(result.misrouted).toBe(false);
    expect(result.warning).toBe(false);
    expect(activities.save).toHaveBeenCalled();
    expect(events.emit).toHaveBeenCalledWith(
      HANDLING_ACTIVITY_REGISTERED_EVENT,
      expect.objectContaining({ bookingId: 'bk-1', trackingNumber: 'TRK-0001', eventType: 'LOAD', misrouted: false }),
    );
    expect(notifier.notifyStatusChange).toHaveBeenCalledWith('bk-1', 'LOAD');
  });

  it('追跡番号が存在しない場合は TrackingNumberNotFoundError', async () => {
    snapshots.findByTrackingNumber.mockResolvedValue(null);
    await expect(service.register(command())).rejects.toThrow(TrackingNumberNotFoundError);
  });

  it('LOAD の場所不一致は MISROUTED としてイベントに載る', async () => {
    const result = await service.register(command({ location: 'HKHKG' }));
    expect(result.misrouted).toBe(true);
    expect(events.emit).toHaveBeenCalledWith(
      HANDLING_ACTIVITY_REGISTERED_EVENT,
      expect.objectContaining({ misrouted: true }),
    );
  });

  it('RECEIVE の場所不一致は警告（MISROUTED ではない）', async () => {
    const result = await service.register(command({ type: 'RECEIVE', location: 'USLAX', voyageNumber: undefined }));
    expect(result.warning).toBe(true);
    expect(result.misrouted).toBe(false);
  });

  it('CLAIM は荷受人確認が必須', async () => {
    customs.isCustomsCleared.mockResolvedValue(true);
    await expect(
      service.register(command({ type: 'CLAIM', location: 'USLAX', voyageNumber: undefined })),
    ).rejects.toThrow(HandlingValidationError);
  });

  it('CLAIM は通関 CLEARED が前提（未クリアはエラー）', async () => {
    customs.isCustomsCleared.mockResolvedValue(false);
    await expect(
      service.register(
        command({ type: 'CLAIM', location: 'USLAX', voyageNumber: undefined, consigneeConfirmation: 'CODE-1' }),
      ),
    ).rejects.toThrow(CustomsNotClearedError);
  });

  it('CLAIM は通関 CLEARED + 荷受人確認で登録でき、精算開始点イベントを発行する', async () => {
    customs.isCustomsCleared.mockResolvedValue(true);
    const result = await service.register(
      command({ type: 'CLAIM', location: 'USLAX', voyageNumber: undefined, consigneeConfirmation: 'CODE-1' }),
    );
    expect(result.misrouted).toBe(false);
    expect(events.emit).toHaveBeenCalledWith(
      HANDLING_ACTIVITY_REGISTERED_EVENT,
      expect.objectContaining({ eventType: 'CLAIM' }),
    );
    // 引取済 = 配送完了は精算処理の開始条件（US16 受入基準 4・Billing 購読は IT7）
    expect(events.emit).toHaveBeenCalledWith(
      'handling.cargo-claimed',
      expect.objectContaining({ bookingId: 'bk-1', trackingNumber: 'TRK-0001' }),
    );
  });

  it('コミット後副作用（通知）の失敗はコマンド失敗にしない（ADR-009）', async () => {
    notifier.notifyStatusChange.mockRejectedValue(new Error('SMTP down'));
    await expect(service.register(command())).resolves.toMatchObject({ misrouted: false });
  });

  it('通知が失敗しても状態伝播イベントは発行される（try 分離）', async () => {
    notifier.notifyStatusChange.mockRejectedValue(new Error('SMTP down'));
    await service.register(command());
    expect(events.emit).toHaveBeenCalledWith(HANDLING_ACTIVITY_REGISTERED_EVENT, expect.anything());
  });

  it('同一種別・同一日時の登録済み作業がある場合は再登録・イベント・通知をスキップする（冪等）', async () => {
    const { HandlingActivity } = await import('../../domain/model/handling-activity.js');
    activities.findByBookingId.mockResolvedValue([
      HandlingActivity.register({
        bookingId: 'bk-1',
        type: 'LOAD',
        location: 'JPTYO',
        completionTime: new Date('2026-09-01T10:00:00Z'),
        voyageNumber: 'V001',
      }),
    ]);
    const result = await service.register(command());
    expect(result.duplicated).toBe(true);
    expect(activities.save).not.toHaveBeenCalled();
    expect(events.emit).not.toHaveBeenCalled();
    expect(notifier.notifyStatusChange).not.toHaveBeenCalled();
  });
});
