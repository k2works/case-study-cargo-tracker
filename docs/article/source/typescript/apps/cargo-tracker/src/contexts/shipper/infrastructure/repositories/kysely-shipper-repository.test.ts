import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createPgMemDatabase } from '../../../../shared/infrastructure/database/pgmem-database.js';
import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { Shipper } from '../../domain/model/shipper.js';
import { KyselyShipperRepository } from './kysely-shipper-repository.js';

describe('KyselyShipperRepository（pg-mem 統合）', () => {
  let db: AppDatabase;
  let repo: KyselyShipperRepository;

  beforeEach(() => {
    db = createPgMemDatabase().db;
    repo = new KyselyShipperRepository(db);
  });

  afterEach(async () => {
    await db.destroy();
  });

  it('個人荷主を保存し ID を採番する', async () => {
    const id = await repo.save(
      Shipper.registerIndividual({ name: '山田太郎', email: 'yamada@example.com' }),
    );
    expect(id).toBeGreaterThan(0);
  });

  it('法人荷主を割引率つきで保存し findByEmail で取得する', async () => {
    await repo.save(
      Shipper.registerCorporate({
        name: '株式会社サンプル',
        email: 'corp@example.com',
        contractNumber: 'CT-1',
        discountRate: 0.25,
      }),
    );
    const found = await repo.findByEmail('corp@example.com');
    expect(found).not.toBeNull();
    expect(found?.contractNumber?.value).toBe('CT-1');
    expect(found?.discountRate.value).toBeCloseTo(0.25, 4);
  });

  it('未登録 Email は null を返す', async () => {
    expect(await repo.findByEmail('none@example.com')).toBeNull();
  });
});
