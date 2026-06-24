package cargotracker.shipper.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.shipper.application.commandservices.{RegisterShipperCommand, ShipperCommandService}
import cargotracker.shipper.application.queryservices.ShipperQueryService
import cargotracker.shipper.domain.model.aggregates.Shipper
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

/** 荷主の一覧表示・新規登録コントローラ（US02・US03）。
  *
  * ビジネスロジック・永続化は [[ShipperCommandService]] / [[ShipperQueryService]] に委譲する。
  */
@Singleton
class ShipperController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    commandService: ShipperCommandService,
    queryService: ShipperQueryService
) extends AbstractController(cc)
    with I18nSupport:

  // IT9 US28: 荷主登録は Sales / MasterAdmin 限定
  private val RegisterAllowedRoles: Set[Role] = Set(Role.Sales, Role.MasterAdmin)

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

  def list(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.shipper.list(queryService.findAll()))
  }

  def newForm(): Action[AnyContent] = authenticated { implicit request =>
    if !request.roles.exists(RegisterAllowedRoles.contains) then Forbidden("荷主登録は Sales / MasterAdmin 限定です (IT9 US28)")
    else Ok(views.html.shipper.formPage(shipperForm, errorMessage = None))
  }

  def create(): Action[AnyContent] = authenticated { implicit request =>
    if !request.roles.exists(RegisterAllowedRoles.contains) then Forbidden("荷主登録は Sales / MasterAdmin 限定です (IT9 US28)")
    else
      shipperForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            BadRequest(
              views.html.shipper.formPage(
                formWithErrors,
                errorMessage = Some("入力内容を確認してください")
              )
            ),
          data => handleRegister(data)
        )
  }

  /** メール重複チェック（htmx 用）。 */
  def checkEmail(email: String): Action[AnyContent] = authenticated { implicit request =>
    queryService.findByEmail(email) match
      case Some(existing) =>
        Ok(
          s"""<div class="alert alert-warning">同一メール荷主が既に存在します（${existing.shipperId.value}）</div>"""
        ).as("text/html")
      case None => Ok("").as("text/html")
  }

  private def handleRegister(data: ShipperForm)(implicit
      request: RequestHeader
  ): Result =
    val result = RegisterShipperCommand
      .from(
        data.shipperType,
        data.name,
        data.email,
        data.phone,
        data.address,
        data.contractNumber,
        data.discountRate
      )
      .flatMap(commandService.register)

    result match
      case Right(shipper) => redirectToList(shipper)
      case Left(msg) => formError(msg)

  private def redirectToList(shipper: Shipper): Result =
    Redirect(
      cargotracker.shipper.interfaces.web.routes.ShipperController.list()
    ).flashing("success" -> s"荷主 ${shipper.shipperId.value} を登録しました")

  private def formError(msg: String)(implicit request: RequestHeader): Result =
    BadRequest(
      views.html.shipper.formPage(shipperForm, errorMessage = Some(msg))
    )
