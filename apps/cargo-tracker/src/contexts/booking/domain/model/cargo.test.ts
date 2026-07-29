import { describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from './cargo.js';
import { CargoItinerary, Leg } from './cargo-itinerary.js';
import { BookingStatus } from './booking-status.js';

function sampleItinerary(): CargoItinerary {
  return CargoItinerary.of([
    Leg.of({
      voyageNumber: 'V001',
      loadLocation: 'JPTYO',
      unloadLocation: 'USLAX',
      loadTime: new Date('2026-09-01T00:00:00Z'),
      unloadTime: new Date('2026-09-20T00:00:00Z'),
    }),
  ]);
}

function routedCargo(): Cargo {
  const cargo = Cargo.book(baseParams());
  cargo.assignToRouting();
  cargo.assignRoute(sampleItinerary());
  return cargo;
}

function baseParams() {
  return {
    shipperId: 1,
    cargoType: CargoType.GENERAL,
    weightKg: 1000,
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: new Date('2026-09-30'),
    consignee: { name: '受取太郎', address: '大阪市', contactEmail: 'uke@example.com' },
  };
}

describe('Cargo 集約', () => {
  it('一般貨物を仮受付（PRELIMINARY）で登録できる', () => {
    const cargo = Cargo.book(baseParams());
    expect(cargo.bookingStatus).toBe(BookingStatus.PRELIMINARY);
    expect(cargo.bookingId.value).toMatch(/^[0-9a-f-]{36}$/);
    expect(cargo.consignee.name).toBe('受取太郎');
  });

  it('危険物は危険物申告が必須', () => {
    expect(() => Cargo.book({ ...baseParams(), cargoType: CargoType.HAZARDOUS })).toThrow();
    const cargo = Cargo.book({
      ...baseParams(),
      cargoType: CargoType.HAZARDOUS,
      hazardous: { hazardousClass: '3', unNumber: 'UN1203', properShippingName: 'Gasoline' },
    });
    expect(cargo.hazardousDeclaration?.hazardousClass).toBe('3');
  });

  it('冷凍貨物は温度管理条件が必須', () => {
    expect(() => Cargo.book({ ...baseParams(), cargoType: CargoType.REFRIGERATED })).toThrow();
    const cargo = Cargo.book({
      ...baseParams(),
      cargoType: CargoType.REFRIGERATED,
      temperature: { minTemperature: -20, maxTemperature: -10, unit: 'CELSIUS' },
    });
    expect(cargo.temperatureRequirement?.minTemperature).toBe(-20);
  });

  it('経路設計者へ引き渡すと ROUTING_IN_PROGRESS になる', () => {
    const cargo = Cargo.book(baseParams());
    cargo.assignToRouting();
    expect(cargo.bookingStatus).toBe(BookingStatus.ROUTING_IN_PROGRESS);
  });

  it('PRELIMINARY 以外からは引き渡しできない', () => {
    const cargo = Cargo.book(baseParams());
    cargo.assignToRouting();
    expect(() => cargo.assignToRouting()).toThrow();
  });

  it('経路（CargoItinerary）を紐付けると ROUTE_PROPOSED になる（US11）', () => {
    const cargo = Cargo.book(baseParams());
    cargo.assignToRouting();
    cargo.assignRoute(sampleItinerary());
    expect(cargo.bookingStatus).toBe(BookingStatus.ROUTE_PROPOSED);
    expect(cargo.cargoItinerary?.legs).toHaveLength(1);
  });

  it('ROUTING_IN_PROGRESS 以外からは経路紐付けできない', () => {
    const cargo = Cargo.book(baseParams());
    expect(() => cargo.assignRoute(sampleItinerary())).toThrow();
  });

  it('旅程の出発地がルート仕様の出発地と異なる場合は紐付けできない', () => {
    const cargo = Cargo.book(baseParams());
    cargo.assignToRouting();
    const wrongOrigin = CargoItinerary.of([
      Leg.of({
        voyageNumber: 'V001',
        loadLocation: 'SGSIN',
        unloadLocation: 'USLAX',
        loadTime: new Date('2026-09-01T00:00:00Z'),
        unloadTime: new Date('2026-09-20T00:00:00Z'),
      }),
    ]);
    expect(() => cargo.assignRoute(wrongOrigin)).toThrow();
  });

  it('旅程の到着予定が到着期限を超える場合は紐付けできない', () => {
    const cargo = Cargo.book(baseParams());
    cargo.assignToRouting();
    const tooLate = CargoItinerary.of([
      Leg.of({
        voyageNumber: 'V001',
        loadLocation: 'JPTYO',
        unloadLocation: 'USLAX',
        loadTime: new Date('2026-09-01T00:00:00Z'),
        unloadTime: new Date('2026-10-15T00:00:00Z'),
      }),
    ]);
    expect(() => cargo.assignRoute(tooLate)).toThrow();
  });

  it('経路提案後に予約を確定すると CONFIRMED になる（US13）', () => {
    const cargo = routedCargo();
    cargo.confirm();
    expect(cargo.bookingStatus).toBe(BookingStatus.CONFIRMED);
  });

  it('経路提案を差し戻すと ROUTING_IN_PROGRESS に戻る（US13 ルート変更希望）', () => {
    const cargo = routedCargo();
    cargo.returnToRouting();
    expect(cargo.bookingStatus).toBe(BookingStatus.ROUTING_IN_PROGRESS);
  });

  it('確定後に追跡番号を発行すると TRACKING_ISSUED になる（US14）', () => {
    const cargo = routedCargo();
    cargo.confirm();
    cargo.issueTracking('TRK-2026-0001');
    expect(cargo.bookingStatus).toBe(BookingStatus.TRACKING_ISSUED);
    expect(cargo.trackingNumber).toBe('TRK-2026-0001');
  });

  it('CONFIRMED 以外からは追跡番号を発行できない', () => {
    const cargo = routedCargo();
    expect(() => cargo.issueTracking('TRK-2026-0002')).toThrow();
  });

  it('任意の状態から予約をキャンセルできる', () => {
    const cargo = routedCargo();
    cargo.cancel();
    expect(cargo.bookingStatus).toBe(BookingStatus.CANCELLED);
  });

  it('キャンセル済みの予約は確定できない', () => {
    const cargo = routedCargo();
    cargo.cancel();
    expect(() => cargo.confirm()).toThrow();
  });
});
