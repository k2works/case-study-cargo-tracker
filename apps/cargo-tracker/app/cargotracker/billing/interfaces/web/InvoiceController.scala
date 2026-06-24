package cargotracker.billing.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.billing.application.commandservices.{
  BillingCommandService,
  ConfirmPaymentCommand,
  GenerateInvoiceCommand,
  IssuePaymentCommand
}
import cargotracker.billing.domain.model.repositories.InvoiceRepository
import cargotracker.billing.domain.model.valueobjects.InvoiceId
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Clock, LocalDate, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}

/** 請求書発行・一覧・詳細画面（US21 / IT6、IT7 0.8 で法人フラグを Shipper 自動判定に変更）。 */
@Singleton
class InvoiceController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    commandService: BillingCommandService,
    repository: InvoiceRepository,
    auditLog: cargotracker.shared.audit.domain.AuditLogPort,
    clock: Clock
) extends AbstractController(cc)
    with I18nSupport:

  private val SettlementAllowedRoles: Set[Role] = Set(Role.Settlement, Role.MasterAdmin)

  private val newInvoiceForm: Form[NewInvoiceFormData] = Form(
    mapping(
      "bookingId" -> nonEmptyText(maxLength = 20)
    )(NewInvoiceFormData.apply)(d => Some(d.bookingId))
  )

  private val issuePaymentForm: Form[IssuePaymentFormData] = Form(
    mapping(
      "dueDate" -> nonEmptyText,
      "referenceCode" -> nonEmptyText(minLength = 1, maxLength = 64)
    )(IssuePaymentFormData.apply)(d => Some((d.dueDate, d.referenceCode)))
  )

  private val confirmPaymentForm: Form[ConfirmPaymentFormData] = Form(
    mapping(
      "paidAt" -> localDateTime("yyyy-MM-dd'T'HH:mm[:ss]")
    )(ConfirmPaymentFormData.apply)(d => Some(d.paidAt))
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
          commandService.generate(GenerateInvoiceCommand(data.bookingId)) match
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
          case Some(inv) =>
            val canSettle = request.roles.exists(SettlementAllowedRoles.contains)
            Ok(views.html.billing.detail(inv, canSettle))
          case None =>
            Redirect(routes.InvoiceController.list())
              .flashing("error" -> s"請求書 $invoiceId が見つかりません")
  }

  /** US23 / IT8 ADR 0019: 支払発行 (NotIssued → Pending)。Settlement / MasterAdmin 限定。 */
  def issuePayment(invoiceId: String): Action[AnyContent] = authenticated { implicit request =>
    val detailRoute = routes.InvoiceController.detail(invoiceId)
    if !request.roles.exists(SettlementAllowedRoles.contains) then
      Redirect(detailRoute).flashing("error" -> "支払発行の権限がありません")
    else
      issuePaymentForm
        .bindFromRequest()
        .fold(
          _ => Redirect(detailRoute).flashing("error" -> "入力内容に誤りがあります (支払期日 / 参照コードは必須)"),
          data =>
            scala.util.Try(LocalDate.parse(data.dueDate)).toOption match
              case None =>
                Redirect(detailRoute).flashing("error" -> s"支払期日の形式が不正です: ${data.dueDate}")
              case Some(due) =>
                commandService.issuePayment(IssuePaymentCommand(invoiceId, due, data.referenceCode)) match
                  case Right(_) =>
                    // IT9 US30: 監査ログ記録
                    auditLog.record(
                      operator = request.username,
                      action = cargotracker.shared.audit.domain.AuditAction.IssuePayment,
                      targetType = "Invoice",
                      targetId = invoiceId,
                      before = Some("""{"paymentStatus":"NotIssued"}"""),
                      after = Some(
                        s"""{"paymentStatus":"Pending","dueDate":"$due","paymentReference":"${data.referenceCode}"}"""
                      )
                    )
                    Redirect(detailRoute).flashing("success" -> s"支払発行しました (期日: $due)")
                  case Left(msg) => Redirect(detailRoute).flashing("error" -> msg)
        )
  }

  /** US23 / IT8 ADR 0019: 入金確認 (Pending|Overdue → Confirmed) + Cargo.Settled 遷移。 */
  def confirmPayment(invoiceId: String): Action[AnyContent] = authenticated { implicit request =>
    val detailRoute = routes.InvoiceController.detail(invoiceId)
    if !request.roles.exists(SettlementAllowedRoles.contains) then
      Redirect(detailRoute).flashing("error" -> "入金確認の権限がありません")
    else
      confirmPaymentForm
        .bindFromRequest()
        .fold(
          _ => Redirect(detailRoute).flashing("error" -> "入力内容に誤りがあります (入金日時は必須)"),
          data =>
            val paidInstant = data.paidAt.atZone(ZoneId.systemDefault()).toInstant
            commandService.confirmPayment(ConfirmPaymentCommand(invoiceId, paidInstant)) match
              case Right(_) =>
                // IT9 US30: 監査ログ記録
                auditLog.record(
                  operator = request.username,
                  action = cargotracker.shared.audit.domain.AuditAction.ConfirmPayment,
                  targetType = "Invoice",
                  targetId = invoiceId,
                  before = Some("""{"paymentStatus":"Pending"}"""),
                  after = Some(s"""{"paymentStatus":"Confirmed","paidAt":"$paidInstant"}""")
                )
                Redirect(detailRoute).flashing("success" -> s"入金を確認しました。予約は Settled 状態に遷移しました")
              case Left(msg) => Redirect(detailRoute).flashing("error" -> msg)
        )
  }

final case class NewInvoiceFormData(bookingId: String)
final case class IssuePaymentFormData(dueDate: String, referenceCode: String)
final case class ConfirmPaymentFormData(paidAt: LocalDateTime)
