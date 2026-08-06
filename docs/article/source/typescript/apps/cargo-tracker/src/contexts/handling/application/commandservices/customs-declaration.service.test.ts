import { beforeEach, describe, expect, it } from 'vitest';
import type { CustomsDeclaration } from '../../domain/model/customs-declaration.js';
import type {
  CustomsDeclarationRepository,
  CustomsHandlingContext,
} from '../../domain/repository/customs-declaration-repository.js';
import { CUSTOMS_HELD_EVENT } from '../../domain/event/customs-held-event.js';
import type { EventPublisher } from './register-handling-activity.service.js';
import {
  CustomsDeclarationService,
  DeclarationNotFoundError,
  HandlingActivityNotFoundError,
} from './customs-declaration.service.js';

/** インメモリ通関申告リポジトリ（ヘキサゴナルの Port テストダブル） */
class InMemoryCustomsDeclarationRepository implements CustomsDeclarationRepository {
  readonly stored = new Map<string, CustomsDeclaration>();
  private readonly contexts = new Map<number, CustomsHandlingContext>();

  setContext(handlingActivityId: number, context: CustomsHandlingContext): void {
    this.contexts.set(handlingActivityId, context);
  }

  async save(declaration: CustomsDeclaration): Promise<void> {
    this.stored.set(declaration.declarationNumber, declaration);
  }

  async update(declaration: CustomsDeclaration): Promise<void> {
    this.stored.set(declaration.declarationNumber, declaration);
  }

  async findByDeclarationNumber(declarationNumber: string): Promise<CustomsDeclaration | null> {
    return this.stored.get(declarationNumber) ?? null;
  }

  async findHandlingContext(handlingActivityId: number): Promise<CustomsHandlingContext | null> {
    return this.contexts.get(handlingActivityId) ?? null;
  }
}

/** 発行イベントを記録するテスト用 EventPublisher */
class RecordingEventPublisher implements EventPublisher {
  readonly emitted: { event: string; payload: unknown }[] = [];
  emit(event: string, payload: unknown): void {
    this.emitted.push({ event, payload });
  }
}

describe('CustomsDeclarationService（US16 前提条件・ADR-010）', () => {
  let repo: InMemoryCustomsDeclarationRepository;
  let events: RecordingEventPublisher;
  let service: CustomsDeclarationService;
  const activityId = 1;

  beforeEach(() => {
    repo = new InMemoryCustomsDeclarationRepository();
    repo.setContext(activityId, { bookingId: 'bk-1', location: 'JPTYO', trackingNumber: 'TRK-XYZ' });
    events = new RecordingEventPublisher();
    service = new CustomsDeclarationService(repo, events);
  });

  it('通関申告を PENDING で登録し、CLEARED へ更新すると clearedAt が設定される', async () => {
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date('2026-09-10T10:00:00Z') });
    expect(declarationNumber).toMatch(/^DECL-/);
    expect(repo.stored.get(declarationNumber)!.status).toBe('PENDING');

    await service.updateStatus(declarationNumber, 'CLEARED');
    const stored = repo.stored.get(declarationNumber)!;
    expect(stored.status).toBe('CLEARED');
    expect(stored.clearedAt).not.toBeNull();
  });

  it('HELD への更新では clearedAt は設定されない', async () => {
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date('2026-09-10T10:00:00Z') });
    await service.updateStatus(declarationNumber, 'HELD');
    const stored = repo.stored.get(declarationNumber)!;
    expect(stored.status).toBe('HELD');
    expect(stored.clearedAt).toBeNull();
  });

  it('HELD → CLEARED は許可され通関済になる（留置解除）', async () => {
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date('2026-09-10T10:00:00Z') });
    await service.updateStatus(declarationNumber, 'HELD');
    await service.updateStatus(declarationNumber, 'CLEARED');
    expect(repo.stored.get(declarationNumber)!.status).toBe('CLEARED');
  });

  it('CLEARED → HELD は不可（終端・仕様変更）', async () => {
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date('2026-09-10T10:00:00Z') });
    await service.updateStatus(declarationNumber, 'CLEARED');
    await expect(service.updateStatus(declarationNumber, 'HELD')).rejects.toThrow();
    expect(repo.stored.get(declarationNumber)!.status).toBe('CLEARED');
  });

  it('HELD 遷移で customs.held イベントを発行する（追跡番号・場所を含む）', async () => {
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date('2026-09-10T10:00:00Z') });
    await service.updateStatus(declarationNumber, 'HELD');
    const held = events.emitted.filter((e) => e.event === CUSTOMS_HELD_EVENT);
    expect(held).toHaveLength(1);
    expect(held[0].payload).toMatchObject({ bookingId: 'bk-1', trackingNumber: 'TRK-XYZ', declarationNumber, location: 'JPTYO' });
  });

  it('CLEARED 遷移では customs.held イベントを発行しない', async () => {
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date('2026-09-10T10:00:00Z') });
    await service.updateStatus(declarationNumber, 'CLEARED');
    expect(events.emitted.filter((e) => e.event === CUSTOMS_HELD_EVENT)).toHaveLength(0);
  });

  it('追跡番号が未発行なら payload.trackingNumber は null', async () => {
    repo.setContext(activityId, { bookingId: 'bk-1', location: 'JPTYO', trackingNumber: null });
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date('2026-09-10T10:00:00Z') });
    await service.updateStatus(declarationNumber, 'HELD');
    const held = events.emitted.filter((e) => e.event === CUSTOMS_HELD_EVENT);
    expect(held[0].payload).toMatchObject({ trackingNumber: null });
  });

  it('存在しない荷役作業への申告登録はエラー', async () => {
    await expect(service.register({ handlingActivityId: 9999, declaredAt: new Date() })).rejects.toThrow(
      HandlingActivityNotFoundError,
    );
  });

  it('存在しない申告番号の更新はエラー', async () => {
    await expect(service.updateStatus('DECL-MISSING', 'CLEARED')).rejects.toThrow(DeclarationNotFoundError);
  });

  it('不正なステータスはエラー', async () => {
    const declarationNumber = await service.register({ handlingActivityId: activityId, declaredAt: new Date() });
    await expect(service.updateStatus(declarationNumber, 'INVALID')).rejects.toThrow();
  });
});
