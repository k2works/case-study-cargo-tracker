import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyShipperRepository } from './infrastructure/repositories/kysely-shipper-repository.js';
import type { ShipperRepository } from './domain/repository/shipper-repository.js';
import { RegisterShipperService } from './application/commandservices/register-shipper.service.js';
import { ShipperController } from './presentation/shipper.controller.js';

export const SHIPPER_REPOSITORY = Symbol('SHIPPER_REPOSITORY');

/**
 * Shipper Context の配線（US02/US03）。
 */
@Module({
  controllers: [ShipperController],
  providers: [
    {
      provide: SHIPPER_REPOSITORY,
      useFactory: (db: AppDatabase): ShipperRepository => new KyselyShipperRepository(db),
      inject: [DATABASE],
    },
    {
      provide: RegisterShipperService,
      useFactory: (repo: ShipperRepository): RegisterShipperService =>
        new RegisterShipperService(repo),
      inject: [SHIPPER_REPOSITORY],
    },
  ],
})
export class ShipperModule {}
