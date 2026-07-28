import type { AppDatabase } from '../../../../shared/infrastructure/database/database.js';
import { isCargoType } from '../../../../shared/domain/model/cargo-type.js';
import { Estimate } from '../../domain/model/estimate.js';
import { RouteCandidate } from '../../domain/model/route-candidate.js';
import { isEstimateStatus } from '../../domain/model/estimate-status.js';
import type { EstimateRepository } from '../../domain/repository/estimate-repository.js';

/** EstimateRepository の Kysely 実装 */
export class KyselyEstimateRepository implements EstimateRepository {
  constructor(private readonly db: AppDatabase) {}

  async save(estimate: Estimate): Promise<number> {
    const inserted = await this.db
      .insertInto('estimate')
      .values({
        estimateId: estimate.estimateId.value,
        originUnlocode: estimate.origin.unlocode,
        destinationUnlocode: estimate.destination.unlocode,
        arrivalDeadline: estimate.arrivalDeadline,
        cargoType: estimate.cargoType,
        weightKg: estimate.weightKg,
        status: estimate.status,
      })
      .returning('id')
      .executeTakeFirstOrThrow();

    if (estimate.candidates.length > 0) {
      await this.db
        .insertInto('route_candidate')
        .values(
          estimate.candidates.map((c, index) => ({
            estimateId: inserted.id,
            voyageNumber: c.voyageNumber,
            transitPort: c.transitPort,
            transitDays: c.transitDays,
            estimatedCost: c.estimatedCost,
            rank: index,
          })),
        )
        .execute();
    }
    return inserted.id;
  }

  async findByEstimateId(estimateId: string): Promise<Estimate | null> {
    const row = await this.db
      .selectFrom('estimate')
      .selectAll()
      .where('estimateId', '=', estimateId)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }
    const candidates = await this.loadCandidates(row.id);
    return this.toEstimate(row, candidates);
  }

  async findAll(): Promise<Estimate[]> {
    const rows = await this.db.selectFrom('estimate').selectAll().orderBy('id').execute();
    const result: Estimate[] = [];
    for (const row of rows) {
      const candidates = await this.loadCandidates(row.id);
      result.push(this.toEstimate(row, candidates));
    }
    return result;
  }

  private async loadCandidates(estimateId: number): Promise<RouteCandidate[]> {
    const rows = await this.db
      .selectFrom('route_candidate')
      .selectAll()
      .where('estimateId', '=', estimateId)
      .orderBy('rank')
      .execute();
    return rows.map((r) =>
      RouteCandidate.of({
        voyageNumber: r.voyageNumber,
        transitPort: r.transitPort,
        transitDays: r.transitDays,
        estimatedCost: Number(r.estimatedCost),
      }),
    );
  }

  private toEstimate(
    row: {
      id: number;
      estimateId: string;
      originUnlocode: string;
      destinationUnlocode: string;
      arrivalDeadline: Date;
      cargoType: string;
      weightKg: string;
      status: string;
    },
    candidates: RouteCandidate[],
  ): Estimate {
    if (!isCargoType(row.cargoType)) {
      throw new Error(`不正な貨物種別: ${row.cargoType}`);
    }
    if (!isEstimateStatus(row.status)) {
      throw new Error(`不正な見積状態: ${row.status}`);
    }
    return Estimate.reconstruct({
      id: row.id,
      estimateId: row.estimateId,
      origin: row.originUnlocode,
      destination: row.destinationUnlocode,
      arrivalDeadline: new Date(row.arrivalDeadline),
      cargoType: row.cargoType,
      weightKg: Number(row.weightKg),
      status: row.status,
      candidates,
    });
  }
}
