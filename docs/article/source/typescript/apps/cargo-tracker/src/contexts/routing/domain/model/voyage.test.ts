import { describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { CarrierMovement, Schedule, Voyage, VoyageNumber } from './voyage.js';

describe('Voyage（航海）', () => {
  it('航海番号・船名・運送会社・対応貨物種別・運送区間を保持する', () => {
    const voyage = Voyage.register({
      voyageNumber: 'V001',
      shipName: 'Pacific Star',
      carrierName: 'Oceanic',
      supportedCargoTypes: [CargoType.GENERAL, CargoType.REFRIGERATED],
      schedule: Schedule.of([
        CarrierMovement.of({
          departureLocation: 'JPTYO',
          arrivalLocation: 'HKHKG',
          departureTime: new Date('2026-09-01T09:00:00Z'),
          arrivalTime: new Date('2026-09-04T10:00:00Z'),
        }),
        CarrierMovement.of({
          departureLocation: 'HKHKG',
          arrivalLocation: 'SGSIN',
          departureTime: new Date('2026-09-05T12:00:00Z'),
          arrivalTime: new Date('2026-09-08T08:00:00Z'),
        }),
      ]),
    });

    expect(voyage.voyageNumber.value).toBe('V001');
    expect(voyage.shipName).toBe('Pacific Star');
    expect(voyage.carrierName).toBe('Oceanic');
    expect(voyage.schedule.carrierMovements).toHaveLength(2);
    expect(voyage.supports(CargoType.GENERAL)).toBe(true);
    expect(voyage.supports(CargoType.HAZARDOUS)).toBe(false);
    expect(voyage.departureTime('JPTYO')).toEqual(new Date('2026-09-01T09:00:00Z'));
    expect(voyage.arrivalTime('SGSIN')).toEqual(new Date('2026-09-08T08:00:00Z'));
  });

  it('航海番号は必須', () => {
    expect(() => VoyageNumber.of('')).toThrow('航海番号は必須です');
  });

  it('出発港と到着港が同一の運送区間を拒否する', () => {
    expect(() =>
      CarrierMovement.of({
        departureLocation: 'JPTYO',
        arrivalLocation: 'JPTYO',
        departureTime: new Date('2026-09-01T09:00:00Z'),
        arrivalTime: new Date('2026-09-02T09:00:00Z'),
      }),
    ).toThrow('出発港と到着港は異なる必要があります');
  });

  it('出発時刻が到着時刻より後の運送区間を拒否する', () => {
    expect(() =>
      CarrierMovement.of({
        departureLocation: 'JPTYO',
        arrivalLocation: 'SGSIN',
        departureTime: new Date('2026-09-03T09:00:00Z'),
        arrivalTime: new Date('2026-09-02T09:00:00Z'),
      }),
    ).toThrow('出発時刻は到着時刻以前である必要があります');
  });

  it('接続しない運送区間のスケジュールを拒否する', () => {
    expect(() =>
      Schedule.of([
        CarrierMovement.of({
          departureLocation: 'JPTYO',
          arrivalLocation: 'HKHKG',
          departureTime: new Date('2026-09-01T09:00:00Z'),
          arrivalTime: new Date('2026-09-04T10:00:00Z'),
        }),
        CarrierMovement.of({
          departureLocation: 'SGSIN',
          arrivalLocation: 'USLAX',
          departureTime: new Date('2026-09-05T12:00:00Z'),
          arrivalTime: new Date('2026-09-10T08:00:00Z'),
        }),
      ]),
    ).toThrow('運送区間は到着港から次の出発港へ接続する必要があります');
  });

  it('前区間の到着前に出発するスケジュールを拒否する', () => {
    expect(() =>
      Schedule.of([
        CarrierMovement.of({
          departureLocation: 'JPTYO',
          arrivalLocation: 'HKHKG',
          departureTime: new Date('2026-09-01T09:00:00Z'),
          arrivalTime: new Date('2026-09-04T10:00:00Z'),
        }),
        CarrierMovement.of({
          departureLocation: 'HKHKG',
          arrivalLocation: 'SGSIN',
          departureTime: new Date('2026-09-04T09:00:00Z'),
          arrivalTime: new Date('2026-09-08T08:00:00Z'),
        }),
      ]),
    ).toThrow('次の運送区間は前区間の到着以降に出発する必要があります');
  });
});
