package cargotracker.shared.audit.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.shared.audit.domain.{AuditAction, AuditLogFilter, AuditLogId, AuditLogPort}
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Instant, LocalDate, ZoneId}
import javax.inject.{Inject, Singleton}

/** IT9 US30: 監査ログ閲覧画面 (MasterAdmin 限定)。
  *
  *   - `/admin/audit-logs`: フィルタ可能な一覧
  *   - `/admin/audit-logs/:id`: 詳細 (before/after JSON 表示)
  *
  * insert は AuditLogPort.record で各 Controller から記録、本 Controller は閲覧のみ提供。
  */
@Singleton
class AuditLogController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    auditLog: AuditLogPort
) extends AbstractController(cc)
    with I18nSupport:

  private val MasterAdminOnly: Set[Role] = Set(Role.MasterAdmin)

  /** 監査ログ一覧 (クエリパラメータ: from / to / operator / action / limit、いずれも省略可)。 */
  def list(): Action[AnyContent] = authenticated { implicit request =>
    if !request.roles.exists(MasterAdminOnly.contains) then Forbidden("監査ログ閲覧は MasterAdmin 限定です")
    else
      val q = request.queryString
      val filter = AuditLogFilter(
        fromOccurredAt = q.get("from").flatMap(_.headOption).flatMap(parseDateStart),
        toOccurredAt = q.get("to").flatMap(_.headOption).flatMap(parseDateEnd),
        operator = q.get("operator").flatMap(_.headOption).filter(_.nonEmpty),
        action = q.get("action").flatMap(_.headOption).filter(_.nonEmpty).flatMap(AuditAction.fromName),
        limit = q.get("limit").flatMap(_.headOption).flatMap(_.toIntOption).getOrElse(100).min(500)
      )
      val logs = auditLog.findByFilter(filter)
      Ok(views.html.admin.auditLogList(logs, filter))
  }

  /** 監査ログ詳細 (before/after JSON 整形表示)。 */
  def detail(id: Long): Action[AnyContent] = authenticated { implicit request =>
    if !request.roles.exists(MasterAdminOnly.contains) then Forbidden("監査ログ閲覧は MasterAdmin 限定です")
    else
      auditLog.findById(AuditLogId(id)) match
        case Some(log) => Ok(views.html.admin.auditLogDetail(log))
        case None => NotFound(s"監査ログ ID=$id が見つかりません")
  }

  private def parseDateStart(s: String): Option[Instant] =
    scala.util.Try(LocalDate.parse(s).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant).toOption

  private def parseDateEnd(s: String): Option[Instant] =
    scala.util.Try(LocalDate.parse(s).plusDays(1).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant).toOption
