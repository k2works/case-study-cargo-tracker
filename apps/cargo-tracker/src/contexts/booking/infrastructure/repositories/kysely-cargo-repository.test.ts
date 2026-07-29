import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import { seedLocations } from '../../../../shared/infrastructure/database/seed.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { Cargo } from '../../domain/model/cargo.js';
import { CargoItinerary, Leg } from '../../domain/model/cargo-itinerary.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import { KyselyCargoRepository } from './kysely-cargo-repository.js';

function sampleItinerary(): CargoItinerary {
  return CargoItinerary.of([
    Leg.of({
      voyageNumber: 'V001',
      loadLocation: 'JPTYO',
      unloadLocation: 'HKHKG',
      loadTime: new Date('2026-09-01T00:00:00Z'),
      unloadTime: new Date('2026-09-04T00:00:00Z'),
    }),
    Leg.of({
      voyageNumber: 'V002',
      loadLocation: 'HKHKG',
      unloadLocation: 'USLAX',
      loadTime: new Date('2026-09-05T00:00:00Z'),
      unloadTime: new Date('2026-09-20T00:00:00Z'),
    }),
  ]);
}

async function seedShipper(db: AppDatabase): Promise<number> {
  const row = await db
    .insertInto('shipper')
    .values({ shipperCode: 'SHP-abc12345', shipperType: 'INDIVIDUAL', name: '荷主', email: 's@example.com' })
    .returning('id')
    .executeTakeFirstOrThrow();
  return row.id;
}

function makeCargo(shipperId: number, overrides = {}): Cargo {
  return Cargo.book({
    shipperId,
    cargoType: CargoType.GENERAL,
    weightKg: 1200,
    origin: 'JPTYO',
    destination: 'USLAX',
    arrivalDeadline: new Date('2026-09-30'),
    consignee: { name: '受取太郎', address: '大阪市', contactEmail: 'uke@example.com' },
    ...overrides,
  });
}

describe('KyselyCargoRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyCargoRepository;
  let shipperId: number;

  beforeEach(async () => {
    db = createPgMemDatabase().db;
    repo = new KyselyCargoRepository(db);
    await seedLocations(db);
    shipperId = await seedShipper(db);
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('貨物予約を保存し bookingId で取得する（荷受人含む）', async () => {
    const cargo = makeCargo(shipperId);
    await repo.save(cargo);
    const found = await repo.findByBookingId(cargo.bookingId.value);
    expect(found).not.toBeNull();
    expect(found?.bookingStatus).toBe(BookingStatus.PRELIMINARY);
    expect(found?.consignee.name).toBe('受取太郎');
    expect(found?.routeSpecification.origin.unlocode).toBe('JPTYO');
  });

  it('危険物貨物の申告を保存・復元する', async () => {
    const cargo = makeCargo(shipperId, {
      cargoType: CargoType.HAZARDOUS,
      hazardous: { hazardousClass: '3', unNumber: 'UN1203', properShippingName: 'Gasoline' },
    });
    await repo.save(cargo);
    const found = await repo.findByBookingId(cargo.bookingId.value);
    expect(found?.hazardousDeclaration?.unNumber).toBe('UN1203');
  });

  it('update で状態遷移（引き渡し）を永続化する', async () => {
    const cargo = makeCargo(shipperId);
    await repo.save(cargo);
    cargo.assignToRouting();
    await repo.update(cargo);
    const found = await repo.findByBookingId(cargo.bookingId.value);
    expect(found?.bookingStatus).toBe(BookingStatus.ROUTING_IN_PROGRESS);
  });

  it('経路紐付け（CargoItinerary）を leg として永続化し復元する', async () => {
    const cargo = makeCargo(shipperId);
    await repo.save(cargo);
    cargo.assignToRouting();
    cargo.assignRoute(sampleItinerary());
    await repo.update(cargo);
    const found = await repo.findByBookingId(cargo.bookingId.value);
    expect(found?.bookingStatus).toBe(BookingStatus.ROUTE_PROPOSED);
    expect(found?.cargoItinerary?.legs).toHaveLength(2);
    expect(found?.cargoItinerary?.legs[0].voyageNumber).toBe('V001');
    expect(found?.cargoItinerary?.legs[1].unloadLocation.unlocode).toBe('USLAX');
  });

  it('再紐付けで leg を入れ替える（旧区間が残らない）', async () => {
    const cargo = makeCargo(shipperId);
    await repo.save(cargo);
    cargo.assignToRouting();
    cargo.assignRoute(sampleItinerary());
    await repo.update(cargo);
    cargo.returnToRouting();
    cargo.assignRoute(
      CargoItinerary.of([
        Leg.of({
          voyageNumber: 'V009',
          loadLocation: 'JPTYO',
          unloadLocation: 'USLAX',
          loadTime: new Date('2026-09-02T00:00:00Z'),
          unloadTime: new Date('2026-09-18T00:00:00Z'),
        }),
      ]),
    );
    await repo.update(cargo);
    const found = await repo.findByBookingId(cargo.bookingId.value);
    expect(found?.cargoItinerary?.legs).toHaveLength(1);
    expect(found?.cargoItinerary?.legs[0].voyageNumber).toBe('V009');
  });

  it('追跡番号の発行を永続化し復元する', async () => {
    const cargo = makeCargo(shipperId);
    await repo.save(cargo);
    cargo.assignToRouting();
    cargo.assignRoute(sampleItinerary());
    cargo.confirm();
    cargo.issueTracking('TRK-2026-0001');
    await repo.update(cargo);
    const found = await repo.findByBookingId(cargo.bookingId.value);
    expect(found?.bookingStatus).toBe(BookingStatus.TRACKING_ISSUED);
    expect(found?.trackingNumber).toBe('TRK-2026-0001');
  });
});
