package cargotracker.estimation.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.estimation.application.commandservices.{CreateEstimateCommand, EstimateCommandService}
import cargotracker.estimation.application.queryservices.EstimateQueryService
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

final case class EstimateFormData(
    origin: String,
    destination: String,
    deadline: LocalDate,
    cargoType: String,
    weightKg: Long
)

@Singleton
class EstimateController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    commandService: EstimateCommandService,
    queryService: EstimateQueryService
) extends AbstractController(cc)
    with I18nSupport:

  private val estimateForm: Form[EstimateFormData] = Form(
    mapping(
      "origin" -> nonEmptyText(minLength = 5, maxLength = 5),
      "destination" -> nonEmptyText(minLength = 5, maxLength = 5),
      "deadline" -> localDate,
      "cargoType" -> nonEmptyText,
      "weightKg" -> longNumber(min = 1, max = 999_999_999L)
    )(EstimateFormData.apply)(d => Some((d.origin, d.destination, d.deadline, d.cargoType, d.weightKg)))
  )

  def list(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.estimate.list(queryService.findAll()))
  }

  def newForm(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.estimate.form(estimateForm, errorMessage = None))
  }

  def create(): Action[AnyContent] = authenticated { implicit request =>
    estimateForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.estimate
              .form(formWithErrors, errorMessage = Some("入力内容を確認してください"))
          ),
        data =>
          commandService.create(
            CreateEstimateCommand(
              origin = data.origin,
              destination = data.destination,
              deadline = data.deadline,
              cargoType = data.cargoType,
              weightKg = data.weightKg
            )
          ) match
            case Right(estimate) =>
              Redirect(
                cargotracker.estimation.interfaces.web.routes.EstimateController
                  .detail(estimate.estimateId.value)
              ).flashing("success" -> "見積を作成しました")
            case Left(msg) =>
              BadRequest(
                views.html.estimate.form(estimateForm, errorMessage = Some(msg))
              )
      )
  }

  def detail(estimateId: String): Action[AnyContent] = authenticated { implicit request =>
    queryService.findById(estimateId) match
      case Some(est) => Ok(views.html.estimate.detail(est))
      case None => NotFound("見積が見つかりません")
  }
