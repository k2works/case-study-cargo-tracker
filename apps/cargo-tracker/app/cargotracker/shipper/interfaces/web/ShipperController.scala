package cargotracker.shipper.interfaces.web

import cargotracker.shared.domain.ShipperType
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.shipper.domain.model.valueobjects.DiscountRate
import play.api.data.Form
import play.api.data.Forms.*
import play.api.data.format.Formats.doubleFormat
import play.api.i18n.I18nSupport
import play.api.mvc.*

import javax.inject.{Inject, Singleton}
final case class ShipperForm(
    name: String,
    email: String,
    phone: String,
    address: String,
    shipperType: String,
    contractNumber: Option[String],
    discountRate: Option[Double]
)

/** 荷主の一覧表示・新規登録コントローラ（US02・US03）。 */
@Singleton
class ShipperController @Inject() (
    cc: ControllerComponents,
    repository: ShipperRepository
) extends AbstractController(cc)
    with I18nSupport:

  private val shipperForm: Form[ShipperForm] = Form(
    mapping(
      "name" -> nonEmptyText(maxLength = 200),
      "email" -> nonEmptyText(maxLength = 200),
      "phone" -> nonEmptyText(maxLength = 50),
      "address" -> nonEmptyText(maxLength = 500),
      "shipperType" -> nonEmptyText,
      "contractNumber" -> optional(text(maxLength = 50)),
      "discountRate" -> optional(of[Double])
    )(ShipperForm.apply)(sf =>
      Some(
        (
          sf.name,
          sf.email,
          sf.phone,
          sf.address,
          sf.shipperType,
          sf.contractNumber,
          sf.discountRate
        )
      )
    )
  )

  def list(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.shipper.list(repository.findAll()))
  }

  def newForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.shipper.form(shipperForm, errorMessage = None))
  }

  def create(): Action[AnyContent] = Action { implicit request =>
    shipperForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.shipper.form(
              formWithErrors,
              errorMessage = Some("入力内容を確認してください")
            )
          ),
        data => persistShipper(data)
      )
  }

  /** メール重複チェック（htmx 用）。 */
  def checkEmail(email: String): Action[AnyContent] = Action { implicit request =>
    repository.findByEmail(email) match
      case Some(existing) =>
        Ok(
          s"""<div class="alert alert-warning">同一メール荷主が既に存在します（${existing.shipperId.value}）</div>"""
        ).as("text/html")
      case None => Ok("").as("text/html")
  }

  private def persistShipper(data: ShipperForm)(implicit
      request: RequestHeader
  ): Result =
    ShipperType.fromName(data.shipperType) match
      case Some(ShipperType.Individual) =>
        Shipper
          .individual(
            repository.nextIdentity(),
            data.name,
            data.email,
            data.phone,
            data.address
          )
          .fold(
            err => formError(s"荷主の生成に失敗しました: $err"),
            shipper => saveAndRedirect(shipper)
          )

      case Some(ShipperType.Corporate) =>
        val rate = data.discountRate
          .flatMap(d => DiscountRate(d).toOption)
          .getOrElse(DiscountRate.zero)
        Shipper
          .corporate(
            repository.nextIdentity(),
            data.name,
            data.email,
            data.phone,
            data.address,
            data.contractNumber.getOrElse(""),
            rate
          )
          .fold(
            err => formError(s"荷主の生成に失敗しました: $err"),
            shipper => saveAndRedirect(shipper)
          )

      case None =>
        formError("荷主種別が不正です")

  private def saveAndRedirect(shipper: Shipper): Result =
    repository.save(shipper)
    Redirect(
      cargotracker.shipper.interfaces.web.routes.ShipperController.list()
    ).flashing("success" -> s"荷主 ${shipper.shipperId.value} を登録しました")

  private def formError(msg: String)(implicit request: RequestHeader): Result =
    BadRequest(
      views.html.shipper.form(shipperForm, errorMessage = Some(msg))
    )
