import type { Estimate } from '../model/estimate.js';

/** 見積リポジトリ出力ポート */
export interface EstimateRepository {
  save(estimate: Estimate): Promise<number>;
  findByEstimateId(estimateId: string): Promise<Estimate | null>;
  findAll(): Promise<Estimate[]>;
}
