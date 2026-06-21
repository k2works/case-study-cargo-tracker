package cargotracker.routing.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.routing.application.commandservices.{
  CarrierMovementCommand,
  RegisterVoyageCommand,
  UpdateVoyageCommand,
  VoyageCommandService
}
import cargotracker.routing.application.queryservices.VoyageQueryService
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Instant, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}

/** 航海スケジュール画面の Controller（US24・US25）。 */
@Singleton
class VoyageController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    commandService: VoyageCommandService,
    queryService: VoyageQueryService
) extends AbstractController(cc)
    with I18nSupport:

  private val voyageForm: Form[VoyageFormData] = Form(
    mapping(
      "voyageNumber" -> nonEmptyText(maxLength = 20),
      "movements" -> seq(
        mapping(
          "departureLocation" -> nonEmptyText(minLength = 5, maxLength = 5),
          "arrivalLocation" -> nonEmptyText(minLength = 5, maxLength = 5),
          "departureTime" -> localDateTime,
          "arrivalTime" -> localDateTime
        )(MovementForm.apply)(m => Some((m.departureLocation, m.arrivalLocation, m.departureTime, m.arrivalTime)))
      )
    )(VoyageFormData.apply)(d => Some((d.voyageNumber, d.movements)))
  )

  def list(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.voyage.list(queryService.findAll()))
  }

  def newForm(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.voyage.formPage(voyageForm, errorMessage = None, isEdit = false))
  }

  def create(): Action[AnyContent] = authenticated { implicit request =>
    voyageForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.voyage.formPage(
              formWithErrors,
              errorMessage = Some("入力内容を確認してください"),
              isEdit = false
            )
          ),
        data =>
          commandService.register(
            RegisterVoyageCommand(
              voyageNumber = data.voyageNumber,
              movements = data.movements.map(toInput)
            )
          ) match
            case Right(voyage) =>
              Redirect(
                cargotracker.routing.interfaces.web.routes.VoyageController.list()
              ).flashing("success" -> s"航海 ${voyage.voyageNumber.value} を登録しました")
            case Left(msg) =>
              BadRequest(
                views.html.voyage
                  .formPage(voyageForm.fill(data), errorMessage = Some(msg), isEdit = false)
              )
      )
  }

  def editForm(voyageNumber: String): Action[AnyContent] = authenticated { implicit request =>
    queryService.findByVoyageNumber(voyageNumber) match
      case Some(v) =>
        val data = VoyageFormData(
          voyageNumber = v.voyageNumber.value,
          movements = v.schedule.carrierMovements.map { cm =>
            MovementForm(
              cm.departureLocation.unLocode,
              cm.arrivalLocation.unLocode,
              LocalDateTime.ofInstant(cm.departureTime, ZoneId.of("UTC")),
              LocalDateTime.ofInstant(cm.arrivalTime, ZoneId.of("UTC"))
            )
          }
        )
        Ok(views.html.voyage.formPage(voyageForm.fill(data), errorMessage = None, isEdit = true))
      case None => NotFound(s"航海 $voyageNumber が見つかりません")
  }

  def update(voyageNumber: String): Action[AnyContent] = authenticated { implicit request =>
    voyageForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.voyage.formPage(
              formWithErrors,
              errorMessage = Some("入力内容を確認してください"),
              isEdit = true
            )
          ),
        data =>
          commandService.update(
            UpdateVoyageCommand(
              voyageNumber = voyageNumber,
              movements = data.movements.map(toInput)
            )
          ) match
            case Right(voyage) =>
              Redirect(
                cargotracker.routing.interfaces.web.routes.VoyageController.list()
              ).flashing("success" -> s"航海 ${voyage.voyageNumber.value} を更新しました")
            case Left(msg) =>
              BadRequest(
                views.html.voyage
                  .formPage(voyageForm.fill(data), errorMessage = Some(msg), isEdit = true)
              )
      )
  }

  private def toInput(m: MovementForm): CarrierMovementCommand =
    CarrierMovementCommand(
      departureLocation = m.departureLocation,
      arrivalLocation = m.arrivalLocation,
      departureTime = m.departureTime.atZone(ZoneId.of("UTC")).toInstant,
      arrivalTime = m.arrivalTime.atZone(ZoneId.of("UTC")).toInstant
    )

final case class VoyageFormData(voyageNumber: String, movements: Seq[MovementForm])
final case class MovementForm(
    departureLocation: String,
    arrivalLocation: String,
    departureTime: LocalDateTime,
    arrivalTime: LocalDateTime
)
