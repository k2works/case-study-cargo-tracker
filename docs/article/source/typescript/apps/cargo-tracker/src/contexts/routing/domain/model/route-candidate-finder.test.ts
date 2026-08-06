import { describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import type { CargoType as CargoTypeValue } from '../../../../shared/domain/model/cargo-type.js';
import { CarrierMovement, Schedule, Voyage } from './voyage.js';
import { RouteCandidateFinder, RoutingQuery } from './route-candidate-finder.js';

describe('RouteCandidateFinder', () => {
  it('直行便を最優先候補として提示する', () => {
    const finder = new RouteCandidateFinder();
    const direct = voyage('DIRECT', [
      movement('JPTYO', 'SGSIN', '2026-09-01T09:00:00Z', '2026-09-08T08:00:00Z'),
    ]);
    const transit = voyage('TRANSIT', [
      movement('JPTYO', 'HKHKG', '2026-09-01T09:00:00Z', '2026-09-04T08:00:00Z'),
      movement('HKHKG', 'SGSIN', '2026-09-05T09:00:00Z', '2026-09-09T08:00:00Z'),
    ]);

    const candidates = finder.find(query(), [transit, direct]);

    expect(candidates.map((candidate) => candidate.voyageNumbers)).toEqual([
      ['DIRECT'],
      ['TRANSIT'],
    ]);
    expect(candidates[0].transitPorts).toEqual([]);
    expect(candidates[0].transitDays).toBe(7);
  });

  it('別航海の寄港地接続を候補にする', () => {
    const finder = new RouteCandidateFinder();
    const firstLeg = voyage('V001', [
      movement('JPTYO', 'HKHKG', '2026-09-01T09:00:00Z', '2026-09-04T08:00:00Z'),
    ]);
    const secondLeg = voyage('V002', [
      movement('HKHKG', 'SGSIN', '2026-09-05T09:00:00Z', '2026-09-09T08:00:00Z'),
    ]);

    const candidates = finder.find(query(), [firstLeg, secondLeg]);

    expect(candidates).toHaveLength(1);
    expect(candidates[0].voyageNumbers).toEqual(['V001', 'V002']);
    expect(candidates[0].transitPorts).toEqual(['HKHKG']);
  });

  it('期限日を超える候補を除外し、同日到着は期限内に含める', () => {
    const finder = new RouteCandidateFinder();
    const sameDay = voyage('SAME_DAY', [
      movement('JPTYO', 'SGSIN', '2026-09-01T09:00:00Z', '2026-09-08T23:59:00Z'),
    ]);
    const late = voyage('LATE', [
      movement('JPTYO', 'SGSIN', '2026-09-01T09:00:00Z', '2026-09-09T00:00:00Z'),
    ]);

    const candidates = finder.find(query({ arrivalDeadline: new Date('2026-09-08T00:00:00Z') }), [
      late,
      sameDay,
    ]);

    expect(candidates.map((candidate) => candidate.voyageNumbers)).toEqual([['SAME_DAY']]);
  });

  it('対応していない貨物種別の航海を除外する', () => {
    const finder = new RouteCandidateFinder();
    const generalOnly = voyage('GENERAL_ONLY', [
      movement('JPTYO', 'SGSIN', '2026-09-01T09:00:00Z', '2026-09-08T08:00:00Z'),
    ]);
    const hazardous = voyage(
      'HAZARDOUS',
      [movement('JPTYO', 'SGSIN', '2026-09-02T09:00:00Z', '2026-09-09T08:00:00Z')],
      [CargoType.HAZARDOUS],
    );

    const candidates = finder.find(query({ cargoType: CargoType.HAZARDOUS }), [generalOnly, hazardous]);

    expect(candidates.map((candidate) => candidate.voyageNumbers)).toEqual([['HAZARDOUS']]);
  });

  it('候補がなければ空配列を返す', () => {
    const finder = new RouteCandidateFinder();
    const candidates = finder.find(query({ destination: 'USLAX' }), [
      voyage('V001', [movement('JPTYO', 'SGSIN', '2026-09-01T09:00:00Z', '2026-09-08T08:00:00Z')]),
    ]);

    expect(candidates).toEqual([]);
  });
});

function query(
  overrides: Partial<{
    origin: string;
    destination: string;
    arrivalDeadline: Date;
    cargoType: CargoTypeValue;
  }> = {},
): RoutingQuery {
  return RoutingQuery.of({
    origin: 'JPTYO',
    destination: 'SGSIN',
    arrivalDeadline: new Date('2026-09-10T00:00:00Z'),
    cargoType: CargoType.GENERAL,
    ...overrides,
  });
}

function voyage(
  voyageNumber: string,
  carrierMovements: CarrierMovement[],
  supportedCargoTypes: CargoTypeValue[] = [CargoType.GENERAL],
): Voyage {
  return Voyage.register({
    voyageNumber,
    shipName: `${voyageNumber} Ship`,
    carrierName: 'Oceanic',
    supportedCargoTypes,
    schedule: Schedule.of(carrierMovements),
  });
}

function movement(
  departureLocation: string,
  arrivalLocation: string,
  departureTime: string,
  arrivalTime: string,
): CarrierMovement {
  return CarrierMovement.of({
    departureLocation,
    arrivalLocation,
    departureTime: new Date(departureTime),
    arrivalTime: new Date(arrivalTime),
  });
}
