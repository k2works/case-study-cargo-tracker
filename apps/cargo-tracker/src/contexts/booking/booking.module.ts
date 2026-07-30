import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyCargoRepository } from './infrastructure/repositories/kysely-cargo-repository.js';
import { KyselyShipperExistenceChecker } from './infrastructure/repositories/kysely-shipper-existence-checker.js';
import { KyselyShipperContact } from './infrastructure/repositories/kysely-shipper-contact.js';
import { KyselyRouteCandidateReader } from './infrastructure/repositories/kysely-route-candidate-reader.js';
import { EventEmitterPublisher } from './infrastructure/services/event-emitter-publisher.js';
import { RecordingNotificationService } from './infrastructure/services/recording-notification-service.js';
import { NotificationRecorder } from '../../shared/infrastructure/notification/notification-recorder.js';
import type { CargoRepository } from './domain/repository/cargo-repository.js';
import type { ShipperExistenceChecker } from './application/outboundservices/acl/shipper-existence-checker.js';
import type { RouteCandidateAcl } from './application/outboundservices/acl/route-candidate-acl.js';
import type { NotificationPort } from './application/outboundservices/acl/notification-port.js';
import type { ShipperContactAcl } from './application/outboundservices/acl/shipper-contact-acl.js';
import { BookCargoService, type EventPublisher } from './application/commandservices/book-cargo.service.js';
import { AssignToRoutingService } from './application/commandservices/assign-to-routing.service.js';
import { RouteCargoService } from './application/commandservices/route-cargo.service.js';
import { ConfirmBookingService } from './application/commandservices/confirm-booking.service.js';
import { AssignTrackingNumberService } from './application/commandservices/assign-tracking-number.service.js';
import { BookingQueryService } from './application/queryservices/booking-query.service.js';
import { CargoBookingController } from './presentation/cargo-booking.controller.js';
import { RouteAssignmentController } from './presentation/route-assignment.controller.js';
import { BookingHandlingRegisteredListener } from './presentation/events/handling-registered.listener.js';
import {
  CARGO_REPOSITORY,
  SHIPPER_EXISTENCE_CHECKER,
  EVENT_PUBLISHER,
  ROUTE_CANDIDATE_ACL,
  NOTIFICATION_PORT,
  SHIPPER_CONTACT_ACL,
} from './booking.tokens.js';

export {
  CARGO_REPOSITORY,
  SHIPPER_EXISTENCE_CHECKER,
  EVENT_PUBLISHER,
  ROUTE_CANDIDATE_ACL,
  NOTIFICATION_PORT,
  SHIPPER_CONTACT_ACL,
};

/**
 * Booking Context の配線（US04〜US06・US09〜US14）。
 */
@Module({
  controllers: [CargoBookingController, RouteAssignmentController],
  providers: [
    EventEmitterPublisher,
    BookingHandlingRegisteredListener,
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
      provide: ROUTE_CANDIDATE_ACL,
      useFactory: (db: AppDatabase): RouteCandidateAcl => new KyselyRouteCandidateReader(db),
      inject: [DATABASE],
    },
    {
      provide: NotificationRecorder,
      useFactory: (db: AppDatabase): NotificationRecorder => new NotificationRecorder(db),
      inject: [DATABASE],
    },
    {
      provide: NOTIFICATION_PORT,
      useFactory: (recorder: NotificationRecorder): NotificationPort =>
        new RecordingNotificationService(recorder),
      inject: [NotificationRecorder],
    },
    {
      provide: SHIPPER_CONTACT_ACL,
      useFactory: (db: AppDatabase): ShipperContactAcl => new KyselyShipperContact(db),
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
      provide: RouteCargoService,
      useFactory: (repo: CargoRepository, publisher: EventPublisher): RouteCargoService =>
        new RouteCargoService(repo, publisher),
      inject: [CARGO_REPOSITORY, EVENT_PUBLISHER],
    },
    {
      provide: ConfirmBookingService,
      useFactory: (
        repo: CargoRepository,
        notifier: NotificationPort,
        shipperContacts: ShipperContactAcl,
      ): ConfirmBookingService => new ConfirmBookingService(repo, notifier, shipperContacts),
      inject: [CARGO_REPOSITORY, NOTIFICATION_PORT, SHIPPER_CONTACT_ACL],
    },
    {
      provide: AssignTrackingNumberService,
      useFactory: (
        repo: CargoRepository,
        notifier: NotificationPort,
        shipperContacts: ShipperContactAcl,
        publisher: EventPublisher,
      ): AssignTrackingNumberService =>
        new AssignTrackingNumberService(repo, notifier, shipperContacts, publisher),
      inject: [CARGO_REPOSITORY, NOTIFICATION_PORT, SHIPPER_CONTACT_ACL, EVENT_PUBLISHER],
    },
    {
      provide: BookingQueryService,
      useFactory: (db: AppDatabase): BookingQueryService => new BookingQueryService(db),
      inject: [DATABASE],
    },
  ],
})
export class BookingModule {}
