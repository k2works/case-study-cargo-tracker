package cargotracker.handling.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.commandservices.BookingCommandService
import cargotracker.handling.application.commandservices.{HandlingCommandService, RegisterHandlingActivityCommand}
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import cargotracker.tracking.application.commandservices.{RecordTrackingEventCommand, TrackingCommandService}
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.domain.model.valueobjects.TrackingNumber
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Instant, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}

/** 荷役管理画面 / 登録（US15）。 */
@Singleton
class HandlingController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    handlingCommandService: HandlingCommandService,
    trackingCommandService: TrackingCommandService,
    bookingCommandService: BookingCommandService,
    handlingRepository: HandlingActivityRepository,
    trackingRepository: TrackingActivityRepository
) extends AbstractController(cc)
    with I18nSupport:

  private val handlingForm: Form[HandlingFormData] = Form(
    mapping(
      "trackingNumber" -> nonEmptyText(maxLength = 20),
      "eventType" -> nonEmptyText,
      "completionDateTime" -> localDateTime("yyyy-MM-dd'T'HH:mm[:ss]"),
      "locationUnLocode" -> nonEmptyText(minLength = 5, maxLength = 5),
      "voyageNumber" -> optional(text(maxLength = 20)),
      "operatorName" -> optional(text(maxLength = 200)),
      "recipientConfirmation" -> optional(text(maxLength = 120))
    )(HandlingFormData.apply)(d =>
      Some(
        (
          d.trackingNumber,
          d.eventType,
          d.completionDateTime,
          d.locationUnLocode,
          d.voyageNumber,
          d.operatorName,
          d.recipientConfirmation
        )
      )
    )
  )

  def list(): Action[AnyContent] = authenticated { implicit request =>
    val activities = handlingRepository.findAll()
    Ok(views.html.handling.list(activities))
  }

  def newForm(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.handling.newForm(handlingForm))
  }

  def create(): Action[AnyContent] = authenticated { implicit request =>
    handlingForm
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(views.html.handling.newForm(formWithErrors)),
        data => orchestrateRegistration(data)
      )
  }

  private def orchestrateRegistration(
      data: HandlingFormData
  )(implicit
      request: cargotracker.auth.interfaces.web.AuthenticatedRequest[?]
  ): Result =
    val listRoute = cargotracker.handling.interfaces.web.routes.HandlingController.list()
    val newRoute = cargotracker.handling.interfaces.web.routes.HandlingController.newForm()
    val completionInstant = data.completionDateTime.atZone(ZoneId.systemDefault()).toInstant

    val result = for
      tn <- TrackingNumber(data.trackingNumber).left.map(_ => "追跡番号の形式が不正です")
      activity <- trackingRepository
        .findByTrackingNumber(tn)
        .toRight(s"追跡番号 ${data.trackingNumber} が見つかりません")
      _ <- handlingCommandService.register(
        RegisterHandlingActivityCommand(
          bookingId = activity.bookingId.value,
          eventType = data.eventType,
          completionTime = completionInstant,
          locationUnLocode = data.locationUnLocode,
          voyageNumber = data.voyageNumber,
          operatorName = data.operatorName,
          routeDeviation = false,
          recipientConfirmation = data.recipientConfirmation.filter(_.nonEmpty)
        )
      )
      _ <- trackingCommandService.recordEvent(
        RecordTrackingEventCommand(
          trackingNumber = data.trackingNumber,
          eventType = data.eventType,
          eventTime = completionInstant,
          locationUnLocode = data.locationUnLocode,
          voyageNumber = data.voyageNumber,
          routeDeviation = false
        )
      )
      _ <- bookingCommandService.logHandlingNotification(
        activity.bookingId.value,
        data.trackingNumber,
        data.eventType,
        data.locationUnLocode
      )
      _ <-
        if data.eventType == "Claim" then
          bookingCommandService
            .completeDelivery(
              activity.bookingId.value,
              data.trackingNumber,
              data.locationUnLocode,
              data.recipientConfirmation.getOrElse("")
            )
            .map(_ => ())
        else Right(())
    yield ()

    result match
      case Right(_) =>
        Redirect(listRoute).flashing(
          "success" -> s"荷役作業を登録しました（${data.trackingNumber} / ${data.eventType} / ${data.locationUnLocode}）"
        )
      case Left(msg) =>
        Redirect(newRoute).flashing("error" -> msg)

/** 荷役登録フォームデータ。 */
final case class HandlingFormData(
    trackingNumber: String,
    eventType: String,
    completionDateTime: LocalDateTime,
    locationUnLocode: String,
    voyageNumber: Option[String],
    operatorName: Option[String],
    recipientConfirmation: Option[String] = None
)
