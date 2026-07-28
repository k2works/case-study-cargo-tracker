import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyEstimateRepository } from './infrastructure/repositories/kysely-estimate-repository.js';
import { StubRouteCandidateCalculator } from './infrastructure/services/stub-route-candidate-calculator.js';
import type { EstimateRepository } from './domain/repository/estimate-repository.js';
import type { RouteCandidateCalculator } from './application/outboundservices/route-candidate-calculator.js';
import { CreateEstimateService } from './application/commandservices/create-estimate.service.js';
import { EstimateQueryService } from './application/queryservices/estimate-query.service.js';
import { EstimateController } from './presentation/estimate.controller.js';

export const ESTIMATE_REPOSITORY = Symbol('ESTIMATE_REPOSITORY');
export const ROUTE_CANDIDATE_CALCULATOR = Symbol('ROUTE_CANDIDATE_CALCULATOR');

/**
 * Estimation Context の配線（US01）。
 */
@Module({
  controllers: [EstimateController],
  providers: [
    {
      provide: ESTIMATE_REPOSITORY,
      useFactory: (db: AppDatabase): EstimateRepository => new KyselyEstimateRepository(db),
      inject: [DATABASE],
    },
    {
      provide: ROUTE_CANDIDATE_CALCULATOR,
      useClass: StubRouteCandidateCalculator,
    },
    {
      provide: CreateEstimateService,
      useFactory: (
        repo: EstimateRepository,
        calculator: RouteCandidateCalculator,
      ): CreateEstimateService => new CreateEstimateService(repo, calculator),
      inject: [ESTIMATE_REPOSITORY, ROUTE_CANDIDATE_CALCULATOR],
    },
    {
      provide: EstimateQueryService,
      useFactory: (db: AppDatabase): EstimateQueryService => new EstimateQueryService(db),
      inject: [DATABASE],
    },
  ],
})
export class EstimationModule {}
