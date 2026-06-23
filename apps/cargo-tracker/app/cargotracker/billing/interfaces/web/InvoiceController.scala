package cargotracker.billing.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.billing.application.commandservices.{BillingCommandService, GenerateInvoiceCommand}
import cargotracker.billing.domain.model.repositories.InvoiceRepository
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, InvoiceId}
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import javax.inject.{Inject, Singleton}

/** 請求書発行・一覧・詳細画面（US21 / IT6）。 */
@Singleton
class InvoiceController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    commandService: BillingCommandService,
    repository: InvoiceRepository
) extends AbstractController(cc)
    with I18nSupport:

  private val newInvoiceForm: Form[NewInvoiceFormData] = Form(
    mapping(
      "bookingId" -> nonEmptyText(maxLength = 20),
      "isCorporate" -> boolean
    )(NewInvoiceFormData.apply)(d => Some((d.bookingId, d.isCorporate)))
  )

  def list(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.billing.list(repository.findAll()))
  }

  def newForm(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.billing.newForm(newInvoiceForm))
  }

  def create(): Action[AnyContent] = authenticated { implicit request =>
    val newRoute = routes.InvoiceController.newForm()
    newInvoiceForm
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(views.html.billing.newForm(formWithErrors)),
        data =>
          commandService.generate(GenerateInvoiceCommand(data.bookingId, data.isCorporate)) match
            case Right(inv) =>
              Redirect(routes.InvoiceController.detail(inv.invoiceId.value))
                .flashing("success" -> s"請求書 ${inv.invoiceId.value} を発行しました")
            case Left(msg) =>
              Redirect(newRoute).flashing("error" -> msg)
      )
  }

  def detail(invoiceId: String): Action[AnyContent] = authenticated { implicit request =>
    InvoiceId(invoiceId) match
      case Left(_) =>
        Redirect(routes.InvoiceController.list()).flashing("error" -> s"請求書 ID の形式が不正です: $invoiceId")
      case Right(id) =>
        repository.findById(id) match
          case Some(inv) => Ok(views.html.billing.detail(inv))
          case None =>
            Redirect(routes.InvoiceController.list())
              .flashing("error" -> s"請求書 $invoiceId が見つかりません")
  }

final case class NewInvoiceFormData(bookingId: String, isCorporate: Boolean)
