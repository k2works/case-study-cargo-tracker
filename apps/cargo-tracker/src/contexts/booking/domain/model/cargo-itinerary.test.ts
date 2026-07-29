import { describe, expect, it } from 'vitest';
import { BookingValidationError } from './booking-validation-error.js';
import { CargoItinerary, Leg } from './cargo-itinerary.js';

function leg(props: {
  voyageNumber?: string;
  load?: string;
  unload?: string;
  loadTime?: string;
  unloadTime?: string;
}): Leg {
  return Leg.of({
    voyageNumber: props.voyageNumber ?? 'V001',
    loadLocation: props.load ?? 'JPTYO',
    unloadLocation: props.unload ?? 'SGSIN',
    loadTime: new Date(props.loadTime ?? '2026-09-01T00:00:00Z'),
    unloadTime: new Date(props.unloadTime ?? '2026-09-08T00:00:00Z'),
  });
}

describe('Leg', () => {
  it('積込・荷降の場所と時刻と航海番号を保持する', () => {
    const l = leg({});
    expect(l.voyageNumber).toBe('V001');
    expect(l.loadLocation.unlocode).toBe('JPTYO');
    expect(l.unloadLocation.unlocode).toBe('SGSIN');
  });

  it('積込場所と荷降場所が同一の場合はエラー', () => {
    expect(() => leg({ load: 'JPTYO', unload: 'JPTYO' })).toThrow(BookingValidationError);
  });

  it('積込時刻が荷降時刻より後の場合はエラー', () => {
    expect(() =>
      leg({ loadTime: '2026-09-08T00:00:00Z', unloadTime: '2026-09-01T00:00:00Z' }),
    ).toThrow(BookingValidationError);
  });

  it('航海番号が空の場合はエラー', () => {
    expect(() => leg({ voyageNumber: '  ' })).toThrow(BookingValidationError);
  });
});

describe('CargoItinerary', () => {
  it('1 つ以上の Leg で構成され、到着予定時刻は最終 Leg の荷降時刻', () => {
    const itinerary = CargoItinerary.of([
      leg({ load: 'JPTYO', unload: 'HKHKG', unloadTime: '2026-09-04T00:00:00Z' }),
      leg({ voyageNumber: 'V002', load: 'HKHKG', unload: 'SGSIN', loadTime: '2026-09-05T00:00:00Z', unloadTime: '2026-09-10T00:00:00Z' }),
    ]);
    expect(itinerary.legs).toHaveLength(2);
    expect(itinerary.expectedArrivalTime().toISOString()).toBe('2026-09-10T00:00:00.000Z');
  });

  it('Leg が空の場合はエラー', () => {
    expect(() => CargoItinerary.of([])).toThrow(BookingValidationError);
  });

  it('連結制約（前区間の荷降地 = 次区間の積込地）を満たさない場合はエラー', () => {
    expect(() =>
      CargoItinerary.of([
        leg({ load: 'JPTYO', unload: 'HKHKG' }),
        leg({ load: 'USLAX', unload: 'SGSIN' }),
      ]),
    ).toThrow(BookingValidationError);
  });
});
