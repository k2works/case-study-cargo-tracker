package cargotracker.handling.infrastructure.acl

import cargotracker.handling.domain.model.ports.TrackingLookupPort
import cargotracker.tracking.application.commandservices.{RecordTrackingEventCommand, TrackingCommandService}
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.TrackingNumber

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** Tracking Context を Handling 用に橋渡しする ACL アダプター（IT7 0.3）。 */
@Singleton
class TrackingAdapter @Inject() (
    trackingRepository: TrackingActivityRepository,
    trackingCommandService: TrackingCommandService
) extends TrackingLookupPort:

  override def findBookingIdByTrackingNumber(trackingNumber: String): Option[String] =
    TrackingNumber(trackingNumber).toOption
      .flatMap(trackingRepository.findByTrackingNumber)
      .map(_.bookingId.value)

  override def recordEvent(
      trackingNumber: String,
      eventType: String,
      eventTime: Instant,
      locationUnLocode: String,
      voyageNumber: Option[String],
      routeDeviation: Boolean
  ): Either[String, Unit] =
    trackingCommandService
      .recordEvent(
        RecordTrackingEventCommand(
          trackingNumber = trackingNumber,
          eventType = eventType,
          eventTime = eventTime,
          locationUnLocode = locationUnLocode,
          voyageNumber = voyageNumber,
          routeDeviation = routeDeviation
        )
      )
      .map(_ => ())
