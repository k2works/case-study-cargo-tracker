package cargotracker.handling.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.handling.application.commandservices.{HandlingOrchestrator, RegisterHandlingFlowInput}
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}

/** 荷役管理画面 / 登録（US15、IT7 0.3 で HandlingOrchestrator 経由に切替）。 */
@Singleton
class HandlingController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    orchestrator: HandlingOrchestrator,
    handlingRepository: HandlingActivityRepository
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
      "recipientConfirmation" -> optional(text(maxLength = 120)),
      "recipientConfirmationType" -> optional(text(maxLength = 20))
    )(HandlingFormData.apply)(d =>
      Some(
        (
          d.trackingNumber,
          d.eventType,
          d.completionDateTime,
          d.locationUnLocode,
          d.voyageNumber,
          d.operatorName,
          d.recipientConfirmation,
          d.recipientConfirmationType
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
    val listRoute = cargotracker.handling.interfaces.web.routes.HandlingController.list()
    val newRoute = cargotracker.handling.interfaces.web.routes.HandlingController.newForm()
    handlingForm
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(views.html.handling.newForm(formWithErrors)),
        data =>
          val input = RegisterHandlingFlowInput(
            trackingNumber = data.trackingNumber,
            eventType = data.eventType,
            completionTime = data.completionDateTime.atZone(ZoneId.systemDefault()).toInstant,
            locationUnLocode = data.locationUnLocode,
            voyageNumber = data.voyageNumber,
            operatorName = data.operatorName,
            recipientConfirmation = data.recipientConfirmation.filter(_.nonEmpty),
            recipientConfirmationType = data.recipientConfirmationType.filter(_.nonEmpty)
          )
          orchestrator.register(input) match
            case Right(_) =>
              Redirect(listRoute).flashing(
                "success" -> s"荷役作業を登録しました（${data.trackingNumber} / ${data.eventType} / ${data.locationUnLocode}）"
              )
            case Left(msg) =>
              Redirect(newRoute).flashing("error" -> msg)
      )
  }

/** 荷役登録フォームデータ。 */
final case class HandlingFormData(
    trackingNumber: String,
    eventType: String,
    completionDateTime: LocalDateTime,
    locationUnLocode: String,
    voyageNumber: Option[String],
    operatorName: Option[String],
    recipientConfirmation: Option[String] = None,
    recipientConfirmationType: Option[String] = None
)
