import type { Voyage } from '../model/voyage.js';

/** 航海リポジトリ出力ポート */
export interface VoyageRepository {
  save(voyage: Voyage): Promise<number>;
  findAll(): Promise<Voyage[]>;
  findByVoyageNumber(voyageNumber: string): Promise<Voyage | null>;
  update(voyage: Voyage): Promise<void>;
}
