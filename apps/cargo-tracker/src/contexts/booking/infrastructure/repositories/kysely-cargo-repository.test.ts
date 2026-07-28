import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CargoType } from '../../../../shared/domain/model/cargo-type.js';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { Cargo } from '../../domain/model/cargo.js';
import { BookingStatus } from '../../domain/model/booking-status.js';
import { KyselyCargoRepository } from './kysely-cargo-repository.js';

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
});
