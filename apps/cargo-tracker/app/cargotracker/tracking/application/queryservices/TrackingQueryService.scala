package cargotracker.tracking.application.queryservices

import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.entities.TrackingActivityEvent
import cargotracker.tracking.domain.model.enums.TrackingStatus
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.{TrackingLocation, TrackingNumber}

import javax.inject.{Inject, Singleton}

/** 追跡情報照会クエリサービス（US18）。 */
@Singleton
class TrackingQueryService @Inject() (repository: TrackingActivityRepository):

  def findByTrackingNumber(rawTn: String): Option[TrackingResult] =
    TrackingNumber(rawTn).toOption.flatMap(repository.findByTrackingNumber).map(toView)

  private def toView(ta: TrackingActivity): TrackingResult =
    TrackingResult(
      trackingNumber = ta.trackingNumber.value,
      bookingId = ta.bookingId.value,
      currentStatus = ta.transportStatus,
      currentLocation = ta.currentLocation,
      events = ta.events
    )

/** 追跡照会の表示用 Read Model（US18）。 */
final case class TrackingResult(
    trackingNumber: String,
    bookingId: String,
    currentStatus: TrackingStatus,
    currentLocation: Option[TrackingLocation],
    events: List[TrackingActivityEvent]
)
