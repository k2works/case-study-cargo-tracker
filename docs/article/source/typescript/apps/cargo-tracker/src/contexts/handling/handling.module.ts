import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyHandlingActivityRepository } from './infrastructure/repositories/kysely-handling-activity-repository.js';
import { KyselyCargoSnapshot } from './infrastructure/repositories/kysely-cargo-snapshot.js';
import { KyselyShipperContact } from './infrastructure/repositories/kysely-shipper-contact.js';
import { RecordingStatusNotificationService } from './infrastructure/services/recording-status-notification.service.js';
import { NotificationRecorder } from '../../shared/infrastructure/notification/notification-recorder.js';
import { CLOCK, type Clock } from '../../shared/infrastructure/clock/clock.js';
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
import { CustomsQueryService } from './application/queryservices/customs-query.service.js';
import { CustomsDeclarationService } from './application/commandservices/customs-declaration.service.js';
import { KyselyCustomsDeclarationRepository } from './infrastructure/repositories/kysely-customs-declaration-repository.js';
import type { CustomsDeclarationRepository } from './domain/repository/customs-declaration-repository.js';
import { HandlingController } from './presentation/handling.controller.js';
import { CustomsController } from './presentation/customs.controller.js';
import {
  HANDLING_ACTIVITY_REPOSITORY,
  CARGO_SNAPSHOT_ACL,
  HANDLING_NOTIFICATION_PORT,
  HANDLING_EVENT_PUBLISHER,
  HANDLING_SHIPPER_CONTACT_PORT,
  CUSTOMS_DECLARATION_REPOSITORY,
} from './handling.tokens.js';

export { HANDLING_ACTIVITY_REPOSITORY, CARGO_SNAPSHOT_ACL, HANDLING_NOTIFICATION_PORT, HANDLING_EVENT_PUBLISHER };

/**
 * Handling Context の配線（US15/US16）。
 */
@Module({
  controllers: [HandlingController, CustomsController],
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
      provide: NotificationRecorder,
      useFactory: (db: AppDatabase): NotificationRecorder => new NotificationRecorder(db),
      inject: [DATABASE],
    },
    {
      provide: HANDLING_NOTIFICATION_PORT,
      useFactory: (
        recorder: NotificationRecorder,
        contacts: ShipperContactPort,
      ): HandlingNotificationPort => new RecordingStatusNotificationService(recorder, contacts),
      inject: [NotificationRecorder, HANDLING_SHIPPER_CONTACT_PORT],
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
      provide: CustomsQueryService,
      useFactory: (db: AppDatabase): CustomsQueryService => new CustomsQueryService(db),
      inject: [DATABASE],
    },
    {
      provide: CUSTOMS_DECLARATION_REPOSITORY,
      useFactory: (db: AppDatabase): CustomsDeclarationRepository => new KyselyCustomsDeclarationRepository(db),
      inject: [DATABASE],
    },
    {
      provide: CustomsDeclarationService,
      useFactory: (
        declarations: CustomsDeclarationRepository,
        events: EventPublisher,
      ): CustomsDeclarationService => new CustomsDeclarationService(declarations, events),
      inject: [CUSTOMS_DECLARATION_REPOSITORY, HANDLING_EVENT_PUBLISHER],
    },
    {
      provide: RegisterHandlingActivityService,
      useFactory: (
        activities: HandlingActivityRepository,
        snapshots: CargoSnapshotAcl,
        customs: HandlingHistoryQueryService,
        events: EventPublisher,
        notifier: HandlingNotificationPort,
        now: Clock,
      ): RegisterHandlingActivityService =>
        new RegisterHandlingActivityService(activities, snapshots, customs, events, notifier, now),
      inject: [
        HANDLING_ACTIVITY_REPOSITORY,
        CARGO_SNAPSHOT_ACL,
        HandlingHistoryQueryService,
        HANDLING_EVENT_PUBLISHER,
        HANDLING_NOTIFICATION_PORT,
        CLOCK,
      ],
    },
  ],
})
export class HandlingModule {}
