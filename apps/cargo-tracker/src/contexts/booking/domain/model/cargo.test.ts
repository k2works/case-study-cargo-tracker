import { describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Cargo } from './cargo.js';
import { BookingStatus } from './booking-status.js';

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
});
