import type { AppDatabase } from './database.js';

export interface LocationOption {
  unlocode: string;
  name: string;
}

/** シード済み location（UN/LOCODE）を一覧取得する。画面の datalist に供給する */
export async function listLocations(db: AppDatabase): Promise<LocationOption[]> {
  return db.selectFrom('location').select(['unlocode', 'name']).orderBy('unlocode').execute();
}
