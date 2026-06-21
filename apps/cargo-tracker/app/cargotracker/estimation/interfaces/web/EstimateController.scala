package cargotracker.estimation.interfaces.web

import cargotracker.estimation.domain.model.aggregates.Estimate
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.domain.model.valueobjects.{EstimateId, RouteCandidate}
import cargotracker.shared.domain.pricing.PricingService
import cargotracker.shared.domain.{CargoType, Location, Weight}
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
    repository: EstimateRepository,
    pricingService: PricingService
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

  def list(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.estimate.list(repository.findAll()))
  }

  def newForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.estimate.form(estimateForm, errorMessage = None))
  }

  def create(): Action[AnyContent] = Action { implicit request =>
    estimateForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.estimate
              .form(formWithErrors, errorMessage = Some("入力内容を確認してください"))
          ),
        data => persistEstimate(data)
      )
  }

  def detail(estimateId: String): Action[AnyContent] = Action { implicit request =>
    repository.findById(EstimateId.unsafeFrom(estimateId)) match
      case Some(est) => Ok(views.html.estimate.detail(est))
      case None => NotFound("見積が見つかりません")
  }

  private def persistEstimate(data: EstimateFormData)(implicit
      request: RequestHeader
  ): Result =
    val result =
      for
        origin <- Location(data.origin).left.map(_ => "出発地の UnLocode 形式が不正です")
        destination <- Location(data.destination).left
          .map(_ => "目的地の UnLocode 形式が不正です")
        cargoType <- CargoType
          .fromName(data.cargoType)
          .toRight("貨物種別が不正です")
        weight <- Weight(data.weightKg).left.map(_ => "重量が不正です")
        cost <- pricingService
          .estimateCost(origin, destination, cargoType, weight, None)
          .left
          .map(_ => "料金算出に失敗しました（出発地と目的地が同一の可能性）")
        candidate = RouteCandidate(
          voyageNumber = "VY-MOCK-001",
          transitPorts = List(origin.unLocode, destination.unLocode),
          transitDays = 14,
          estimatedCost = cost
        )
        estimate <- Estimate
          .create(
            origin,
            destination,
            data.deadline,
            cargoType,
            weight,
            List(candidate)
          )
          .left
          .map(_ => "見積の生成に失敗しました")
      yield estimate

    result match
      case Right(estimate) =>
        repository.save(estimate)
        Redirect(
          cargotracker.estimation.interfaces.web.routes.EstimateController
            .detail(estimate.estimateId.value)
        ).flashing("success" -> "見積を作成しました")
      case Left(msg) =>
        BadRequest(
          views.html.estimate.form(estimateForm, errorMessage = Some(msg))
        )
