import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { RegisterVoyageService } from './application/commandservices/register-voyage.service.js';
import { UpdateScheduleService } from './application/commandservices/update-schedule.service.js';
import { VoyageQueryService } from './application/queryservices/voyage-query.service.js';
import type { VoyageRepository } from './domain/repository/voyage-repository.js';
import { KyselyVoyageRepository } from './infrastructure/repositories/kysely-voyage-repository.js';
import { VoyageController } from './presentation/voyage.controller.js';

export const VOYAGE_REPOSITORY = Symbol('VOYAGE_REPOSITORY');

@Module({
  controllers: [VoyageController],
  providers: [
    {
      provide: VOYAGE_REPOSITORY,
      useFactory: (db: AppDatabase): VoyageRepository => new KyselyVoyageRepository(db),
      inject: [DATABASE],
    },
    {
      provide: RegisterVoyageService,
      useFactory: (repo: VoyageRepository): RegisterVoyageService => new RegisterVoyageService(repo),
      inject: [VOYAGE_REPOSITORY],
    },
    {
      provide: UpdateScheduleService,
      useFactory: (repo: VoyageRepository): UpdateScheduleService => new UpdateScheduleService(repo),
      inject: [VOYAGE_REPOSITORY],
    },
    {
      provide: VoyageQueryService,
      useFactory: (db: AppDatabase): VoyageQueryService => new VoyageQueryService(db),
      inject: [DATABASE],
    },
  ],
})
export class RoutingModule {}
