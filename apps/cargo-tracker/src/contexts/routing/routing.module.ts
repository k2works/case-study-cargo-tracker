import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { RegisterVoyageService } from './application/commandservices/register-voyage.service.js';
import { UpdateScheduleService } from './application/commandservices/update-schedule.service.js';
import { VoyageQueryService } from './application/queryservices/voyage-query.service.js';
import type { VoyageRepository } from './domain/repository/voyage-repository.js';
import { KyselyVoyageRepository } from './infrastructure/repositories/kysely-voyage-repository.js';
import { VoyageController } from './presentation/voyage.controller.js';
import type { RoutingBookingConditionReader } from './application/outboundservices/acl/routing-booking-condition-reader.js';
import { KyselyRoutingBookingConditionReader } from './infrastructure/acl/kysely-routing-booking-condition-reader.js';
import {
  EXTERNAL_ROUTING_SERVICE,
  ROUTING_BOOKING_CONDITION_READER,
  VOYAGE_REPOSITORY,
} from './routing.tokens.js';
import { RoutingCandidateController } from './presentation/routing-candidate.controller.js';
import type { ExternalRoutingServicePort } from './application/outboundservices/external-routing-service-port.js';
import { RouteCandidateFinder } from './domain/model/route-candidate-finder.js';
import { FallbackExternalRoutingService } from './infrastructure/services/fallback-external-routing-service.js';
import { HttpExternalRoutingService } from './infrastructure/services/http-external-routing-service.js';

@Module({
  controllers: [VoyageController, RoutingCandidateController],
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
    {
      provide: ROUTING_BOOKING_CONDITION_READER,
      useFactory: (db: AppDatabase): RoutingBookingConditionReader =>
        new KyselyRoutingBookingConditionReader(db),
      inject: [DATABASE],
    },
    {
      provide: EXTERNAL_ROUTING_SERVICE,
      useFactory: (): ExternalRoutingServicePort =>
        new FallbackExternalRoutingService(
          new HttpExternalRoutingService(process.env.ROUTING_SERVICE_BASE_URL ?? 'http://localhost:65535'),
          new RouteCandidateFinder(),
        ),
    },
  ],
})
export class RoutingModule {}
