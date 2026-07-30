import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyHandlingActivityRepository } from './infrastructure/repositories/kysely-handling-activity-repository.js';
import { KyselyCargoSnapshot } from './infrastructure/repositories/kysely-cargo-snapshot.js';
import { KyselyShipperContact } from './infrastructure/repositories/kysely-shipper-contact.js';
import { RecordingStatusNotificationService } from './infrastructure/services/recording-status-notification.service.js';
import { HandlingEventEmitterPublisher } from './infrastructure/services/event-emitter-publisher.js';
import type { HandlingActivityRepository } from './domain/repository/handling-activity-repository.js';
import type { CargoSnapshotAcl } from './application/outboundservices/acl/cargo-snapshot-acl.js';
import type { HandlingNotificationPort } from './application/outboundservices/acl/handling-notification-port.js';
import type { ShipperContactPort } from './application/outboundservices/acl/shipper-contact-port.js';
import {
  RegisterHandlingActivityService,
  type EventPublisher,
} from './application/commandservices/register-handling-activity.service.js';
import { HandlingHistoryQueryService } from './application/queryservices/handling-history-query.service.js';
import { CustomsDeclarationService } from './application/commandservices/customs-declaration.service.js';
import { HandlingController } from './presentation/handling.controller.js';
import {
  HANDLING_ACTIVITY_REPOSITORY,
  CARGO_SNAPSHOT_ACL,
  HANDLING_NOTIFICATION_PORT,
  HANDLING_EVENT_PUBLISHER,
  HANDLING_SHIPPER_CONTACT_PORT,
} from './handling.tokens.js';

export { HANDLING_ACTIVITY_REPOSITORY, CARGO_SNAPSHOT_ACL, HANDLING_NOTIFICATION_PORT, HANDLING_EVENT_PUBLISHER };

/**
 * Handling Context の配線（US15/US16）。
 */
@Module({
  controllers: [HandlingController],
  providers: [
    HandlingEventEmitterPublisher,
    {
      provide: HANDLING_ACTIVITY_REPOSITORY,
      useFactory: (db: AppDatabase): HandlingActivityRepository => new KyselyHandlingActivityRepository(db),
      inject: [DATABASE],
    },
    {
      provide: CARGO_SNAPSHOT_ACL,
      useFactory: (db: AppDatabase): CargoSnapshotAcl => new KyselyCargoSnapshot(db),
      inject: [DATABASE],
    },
    {
      provide: HANDLING_SHIPPER_CONTACT_PORT,
      useFactory: (db: AppDatabase): ShipperContactPort => new KyselyShipperContact(db),
      inject: [DATABASE],
    },
    {
      provide: HANDLING_NOTIFICATION_PORT,
      useFactory: (db: AppDatabase, contacts: ShipperContactPort): HandlingNotificationPort =>
        new RecordingStatusNotificationService(db, contacts),
      inject: [DATABASE, HANDLING_SHIPPER_CONTACT_PORT],
    },
    {
      provide: HANDLING_EVENT_PUBLISHER,
      useExisting: HandlingEventEmitterPublisher,
    },
    {
      provide: HandlingHistoryQueryService,
      useFactory: (db: AppDatabase): HandlingHistoryQueryService => new HandlingHistoryQueryService(db),
      inject: [DATABASE],
    },
    {
      provide: CustomsDeclarationService,
      useFactory: (db: AppDatabase): CustomsDeclarationService => new CustomsDeclarationService(db),
      inject: [DATABASE],
    },
    {
      provide: RegisterHandlingActivityService,
      useFactory: (
        activities: HandlingActivityRepository,
        snapshots: CargoSnapshotAcl,
        customs: HandlingHistoryQueryService,
        events: EventPublisher,
        notifier: HandlingNotificationPort,
      ): RegisterHandlingActivityService =>
        new RegisterHandlingActivityService(activities, snapshots, customs, events, notifier),
      inject: [
        HANDLING_ACTIVITY_REPOSITORY,
        CARGO_SNAPSHOT_ACL,
        HandlingHistoryQueryService,
        HANDLING_EVENT_PUBLISHER,
        HANDLING_NOTIFICATION_PORT,
      ],
    },
  ],
})
export class HandlingModule {}
