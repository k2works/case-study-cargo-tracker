import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';

/** 見積一覧行 DTO（画面表示用フラット構造） */
export interface EstimateListItem {
  estimateId: string;
  origin: string;
  destination: string;
  cargoType: string;
  weightKg: string;
  arrivalDeadline: Date;
  status: string;
}

/** 見積詳細 DTO */
export interface EstimateDetail extends EstimateListItem {
  candidates: {
    voyageNumber: string;
    transitPort: string | null;
    transitDays: number;
    estimatedCost: string;
  }[];
}

/**
 * 見積クエリサービス（CQRS 読み取り側）。
 * ドメインモデルを経由せず、Kysely で画面表示用 DTO を直接返す。
 */
export class EstimateQueryService {
  constructor(private readonly db: AppDatabase) {}

  async list(): Promise<EstimateListItem[]> {
    return this.db
      .selectFrom('estimate')
      .select([
        'estimateId',
        'originUnlocode as origin',
        'destinationUnlocode as destination',
        'cargoType',
        'weightKg',
        'arrivalDeadline',
        'status',
      ])
      .orderBy('id', 'desc')
      .execute();
  }

  async findDetail(estimateId: string): Promise<EstimateDetail | null> {
    const estimate = await this.db
      .selectFrom('estimate')
      .select([
        'id',
        'estimateId',
        'originUnlocode as origin',
        'destinationUnlocode as destination',
        'cargoType',
        'weightKg',
        'arrivalDeadline',
        'status',
      ])
      .where('estimateId', '=', estimateId)
      .executeTakeFirst();
    if (estimate === undefined) {
      return null;
    }
    const candidates = await this.db
      .selectFrom('route_candidate')
      .select(['voyageNumber', 'transitPort', 'transitDays', 'estimatedCost'])
      .where('estimateId', '=', estimate.id)
      .orderBy('rank')
      .execute();
    return {
      estimateId: estimate.estimateId,
      origin: estimate.origin,
      destination: estimate.destination,
      cargoType: estimate.cargoType,
      weightKg: estimate.weightKg,
      arrivalDeadline: estimate.arrivalDeadline,
      status: estimate.status,
      candidates,
    };
  }
}
