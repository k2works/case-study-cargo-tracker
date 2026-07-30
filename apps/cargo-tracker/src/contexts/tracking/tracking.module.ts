import { Module } from '@nestjs/common';
import { DATABASE, type AppDatabase } from '../../shared/infrastructure/database/database.js';
import { KyselyTrackingActivityRepository } from './infrastructure/repositories/kysely-tracking-activity-repository.js';
import { KyselyShipperContact } from './infrastructure/repositories/kysely-shipper-contact.js';
import { KyselyItinerarySnapshot } from './infrastructure/repositories/kysely-itinerary-snapshot.js';
import { RecordingTrackingNotificationService } from './infrastructure/services/recording-tracking-notification.service.js';
import { NotificationRecorder } from '../../shared/infrastructure/notification/notification-recorder.js';
import { CLOCK, type Clock } from '../../shared/infrastructure/clock/clock.js';
import type { TrackingActivityRepository } from './domain/repository/tracking-activity-repository.js';
import type { TrackingNotificationPort } from './application/outboundservices/acl/tracking-notification-port.js';
import type { ShipperContactPort } from './application/outboundservices/acl/shipper-contact-port.js';
import type { ItinerarySnapshotPort } from './application/outboundservices/acl/itinerary-snapshot-port.js';
import { TrackCargoService } from './application/commandservices/track-cargo.service.js';
import { RegisterExceptionService } from './application/commandservices/register-exception.service.js';
import { TrackingQueryService } from './application/queryservices/tracking-query.service.js';
import { TrackingController } from './presentation/tracking.controller.js';
import { PublicTrackingController } from './presentation/public-tracking.controller.js';
import { TrackingEventListener } from './presentation/events/tracking-event.listener.js';
import {
  TRACKING_ACTIVITY_REPOSITORY,
  TRACKING_NOTIFICATION_PORT,
  TRACKING_SHIPPER_CONTACT_PORT,
  TRACKING_ITINERARY_SNAPSHOT_PORT,
} from './tracking.tokens.js';

export { TRACKING_ACTIVITY_REPOSITORY, TRACKING_NOTIFICATION_PORT };

/**
 * Tracking Context の配線（US17・追跡番号発行/荷役イベント購読）。
 */
@Module({
  controllers: [TrackingController, PublicTrackingController],
  providers: [
    TrackingEventListener,
    {
      provide: TRACKING_ACTIVITY_REPOSITORY,
      useFactory: (db: AppDatabase): TrackingActivityRepository => new KyselyTrackingActivityRepository(db),
      inject: [DATABASE],
    },
    {
      provide: TRACKING_SHIPPER_CONTACT_PORT,
      useFactory: (db: AppDatabase): ShipperContactPort => new KyselyShipperContact(db),
      inject: [DATABASE],
    },
    {
      provide: NotificationRecorder,
      useFactory: (db: AppDatabase): NotificationRecorder => new NotificationRecorder(db),
      inject: [DATABASE],
    },
    {
      provide: TRACKING_NOTIFICATION_PORT,
      useFactory: (
        recorder: NotificationRecorder,
        contacts: ShipperContactPort,
      ): TrackingNotificationPort => new RecordingTrackingNotificationService(recorder, contacts),
      inject: [NotificationRecorder, TRACKING_SHIPPER_CONTACT_PORT],
    },
    {
      provide: TrackCargoService,
      useFactory: (
        activities: TrackingActivityRepository,
        notifier: TrackingNotificationPort,
        now: Clock,
      ): TrackCargoService => new TrackCargoService(activities, notifier, now),
      inject: [TRACKING_ACTIVITY_REPOSITORY, TRACKING_NOTIFICATION_PORT, CLOCK],
    },
    {
      provide: RegisterExceptionService,
      useFactory: (
        activities: TrackingActivityRepository,
        notifier: TrackingNotificationPort,
        now: Clock,
      ): RegisterExceptionService => new RegisterExceptionService(activities, notifier, now),
      inject: [TRACKING_ACTIVITY_REPOSITORY, TRACKING_NOTIFICATION_PORT, CLOCK],
    },
    {
      provide: TRACKING_ITINERARY_SNAPSHOT_PORT,
      useFactory: (db: AppDatabase): ItinerarySnapshotPort => new KyselyItinerarySnapshot(db),
      inject: [DATABASE],
    },
    {
      provide: TrackingQueryService,
      useFactory: (db: AppDatabase, itinerary: ItinerarySnapshotPort): TrackingQueryService =>
        new TrackingQueryService(db, itinerary),
      inject: [DATABASE, TRACKING_ITINERARY_SNAPSHOT_PORT],
    },
  ],
})
export class TrackingModule {}
