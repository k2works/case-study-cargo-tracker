import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyCargoRepository } from './infrastructure/repositories/kysely-cargo-repository.js';
import { KyselyShipperExistenceChecker } from './infrastructure/repositories/kysely-shipper-existence-checker.js';
import { EventEmitterPublisher } from './infrastructure/services/event-emitter-publisher.js';
import type { CargoRepository } from './domain/repository/cargo-repository.js';
import type { ShipperExistenceChecker } from './application/outboundservices/acl/shipper-existence-checker.js';
import { BookCargoService, type EventPublisher } from './application/commandservices/book-cargo.service.js';
import { AssignToRoutingService } from './application/commandservices/assign-to-routing.service.js';
import { BookingQueryService } from './application/queryservices/booking-query.service.js';
import { CargoBookingController } from './presentation/cargo-booking.controller.js';

export const CARGO_REPOSITORY = Symbol('CARGO_REPOSITORY');
export const SHIPPER_EXISTENCE_CHECKER = Symbol('SHIPPER_EXISTENCE_CHECKER');
export const EVENT_PUBLISHER = Symbol('EVENT_PUBLISHER');

/**
 * Booking Context の配線（US04/US05/US06）。
 */
@Module({
  controllers: [CargoBookingController],
  providers: [
    EventEmitterPublisher,
    {
      provide: CARGO_REPOSITORY,
      useFactory: (db: AppDatabase): CargoRepository => new KyselyCargoRepository(db),
      inject: [DATABASE],
    },
    {
      provide: SHIPPER_EXISTENCE_CHECKER,
      useFactory: (db: AppDatabase): ShipperExistenceChecker => new KyselyShipperExistenceChecker(db),
      inject: [DATABASE],
    },
    {
      provide: EVENT_PUBLISHER,
      useExisting: EventEmitterPublisher,
    },
    {
      provide: BookCargoService,
      useFactory: (
        repo: CargoRepository,
        checker: ShipperExistenceChecker,
        publisher: EventPublisher,
      ): BookCargoService => new BookCargoService(repo, checker, publisher),
      inject: [CARGO_REPOSITORY, SHIPPER_EXISTENCE_CHECKER, EVENT_PUBLISHER],
    },
    {
      provide: AssignToRoutingService,
      useFactory: (repo: CargoRepository, publisher: EventPublisher): AssignToRoutingService =>
        new AssignToRoutingService(repo, publisher),
      inject: [CARGO_REPOSITORY, EVENT_PUBLISHER],
    },
    {
      provide: BookingQueryService,
      useFactory: (db: AppDatabase): BookingQueryService => new BookingQueryService(db),
      inject: [DATABASE],
    },
  ],
})
export class BookingModule {}
